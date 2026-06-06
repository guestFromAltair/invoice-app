package com.invoiceapp.backend.invoice.service;

import com.invoiceapp.backend.auth.domain.User;
import com.invoiceapp.backend.client.domain.Client;
import com.invoiceapp.backend.client.domain.ClientRepository;
import com.invoiceapp.backend.invoice.domain.*;
import com.invoiceapp.backend.notification.service.NotificationService;
import com.invoiceapp.backend.shared.audit.AuditAction;
import com.invoiceapp.backend.shared.audit.AuditService;
import com.invoiceapp.backend.shared.exception.InvoiceAppException;
import com.invoiceapp.backend.shared.metrics.InvoiceMetrics;
import com.invoiceapp.backend.shared.security.CurrentUserResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Year;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InvoiceService {

    private final InvoiceRepository invoiceRepository;
    private final PaymentRepository paymentRepository;
    private final ClientRepository clientRepository;
    private final NotificationService notificationService;
    private final InvoiceMetrics invoiceMetrics;
    private final AuditService auditService;
    private final CurrentUserResolver currentUserResolver;

    private static final String INVOICE = "INVOICE";
    private static final String INVOICE_STATUS_CHANGED = "INVOICE_STATUS_CHANGED";
    private static final String MANUAL_MARK_PAID = "MANUAL_MARK_PAID";

    public record LineItemRequest(
            String description,
            BigDecimal quantity,
            BigDecimal unitPrice,
            BigDecimal discountPct,
            Integer position
    ) {}

    public record InvoiceRequest(
            UUID clientId,
            LocalDate issueDate,
            LocalDate dueDate,
            BigDecimal taxRate,
            String notes,
            List<LineItemRequest> lineItems
    ) {}

    public record LineItemResponse(
            UUID id,
            String description,
            BigDecimal quantity,
            BigDecimal unitPrice,
            BigDecimal discountPct,
            BigDecimal lineTotal,
            Integer position
    ) {}

    public record InvoiceResponse(
            UUID id,
            String invoiceNumber,
            String clientName,
            UUID clientId,
            InvoiceStatus status,
            LocalDate issueDate,
            LocalDate dueDate,
            BigDecimal subtotal,
            BigDecimal taxRate,
            BigDecimal taxAmount,
            BigDecimal total,
            BigDecimal amountPaid,
            BigDecimal remainingBalance,
            String notes,
            List<LineItemResponse> lineItems,
            Instant createdAt,
            Long version
    ) {}

    public record DashboardStatsResponse(
            BigDecimal totalInvoiced,
            BigDecimal outstandingBalance
    ) {}

    private String generateInvoiceNumber() {
        Long seq = invoiceRepository.nextInvoiceSequence();
        int year = Year.now().getValue();
        return String.format("INV-%d-%05d", year, seq);
    }

    @Transactional
    public InvoiceResponse create(InvoiceRequest request) {
        User user = currentUserResolver.resolveUser();

        Client client = clientRepository
                .findByIdAndOwnerId(request.clientId(), user.getId())
                .orElseThrow(() -> new InvoiceAppException("Client not found", HttpStatus.NOT_FOUND));

        if (request.dueDate().isBefore(request.issueDate())) {
            throw new InvoiceAppException(
                    "Due date cannot be before issue date",
                    HttpStatus.UNPROCESSABLE_CONTENT
            );
        }

        Invoice invoice = Invoice.builder()
                .invoiceNumber(generateInvoiceNumber())
                .client(client)
                .createdBy(user)
                .issueDate(request.issueDate())
                .dueDate(request.dueDate())
                .taxRate(request.taxRate() != null
                        ? request.taxRate()
                        : BigDecimal.ZERO)
                .notes(request.notes())
                .build();

        if (request.lineItems() != null) {
            List<LineItem> items = request.lineItems().stream()
                    .map(req -> LineItem.builder()
                            .invoice(invoice)
                            .description(req.description())
                            .quantity(req.quantity())
                            .unitPrice(req.unitPrice())
                            .discountPct(req.discountPct() != null
                                    ? req.discountPct()
                                    : BigDecimal.ZERO)
                            .position(req.position() != null ? req.position() : 0)
                            .build())
                    .toList();
            invoice.getLineItems().addAll(items);
        }

        invoice.recalculateTotals();
        Invoice saved = invoiceRepository.save(invoice);

        Map<String, Object> newState = snapshotInvoiceState(saved);
        auditService.log(
                INVOICE,
                saved.getId(),
                AuditAction.INVOICE_CREATED,
                null,
                newState,
                user.getId()
        );

        invoiceMetrics.recordInvoiceCreated();
        return toResponse(saved);
    }

    public Page<InvoiceResponse> findAll(InvoiceStatus status, UUID clientId, Pageable pageable) {
        User user = currentUserResolver.resolveUser();
        return invoiceRepository
                .findAllByFilters(user.getId(), status, clientId, pageable)
                .map(this::toResponse);
    }

    public InvoiceResponse findById(UUID id) {
        User user = currentUserResolver.resolveUser();
        Invoice invoice = invoiceRepository
                .findByIdAndCreatedById(id, user.getId())
                .orElseThrow(() -> new InvoiceAppException("Invoice not found", HttpStatus.NOT_FOUND));
        return toResponse(invoice);
    }

    @Transactional
    public InvoiceResponse update(
            UUID id,
            Long version,
            LocalDate issueDate,
            LocalDate dueDate,
            BigDecimal taxRate,
            String notes,
            List<LineItemRequest> lineItems
    ) {
        User user = currentUserResolver.resolveUser();
        Invoice invoice = invoiceRepository
                .findByIdAndCreatedById(id, user.getId())
                .orElseThrow(() -> new InvoiceAppException("Invoice not found", HttpStatus.NOT_FOUND));

        if (version != null && !invoice.getVersion().equals(version)) {
            throw new ObjectOptimisticLockingFailureException(Invoice.class, id);
        }

        if (invoice.getStatus() != InvoiceStatus.DRAFT) {
            throw new InvoiceAppException("Only DRAFT invoices can be edited", HttpStatus.UNPROCESSABLE_CONTENT);
        }

        if (dueDate.isBefore(issueDate)) {
            throw new InvoiceAppException("Due date cannot be before issue date", HttpStatus.UNPROCESSABLE_CONTENT);
        }

        if (lineItems == null || lineItems.isEmpty()) {
            throw new InvoiceAppException("An invoice must contain at least one line item", HttpStatus.UNPROCESSABLE_CONTENT);
        }

        Map<String, Object> oldState = snapshotInvoiceState(invoice);

        invoice.setIssueDate(issueDate);
        invoice.setDueDate(dueDate);
        invoice.setTaxRate(taxRate != null ? taxRate : BigDecimal.ZERO);
        invoice.setNotes(notes);

        // Hibernate issues delete current LineItems first upon commit (before we create a new list of LineItems)
        invoice.getLineItems().clear();

        List<LineItem> newItems = lineItems.stream()
                .map(req -> {
                    LineItem li = LineItem.builder()
                            .invoice(invoice)
                            .description(req.description())
                            .quantity(req.quantity())
                            .unitPrice(req.unitPrice())
                            .discountPct(req.discountPct() != null ? req.discountPct() : BigDecimal.ZERO)
                            .position(req.position() != null ? req.position() : 0)
                            .build();
                    li.computeLineTotal();
                    return li;
                })
                .toList();

        // Now that the current items have been deleted, we create a new (updated) list of LineItems
        invoice.getLineItems().addAll(newItems);
        invoice.recalculateTotals();

        Map<String, Object> newState = snapshotInvoiceState(invoice);

        auditService.log(
                INVOICE,
                invoice.getId(),
                AuditAction.INVOICE_UPDATED,
                oldState,
                newState,
                user.getId()
        );

        return toResponse(invoice);
    }

    @Transactional
    public InvoiceResponse send(UUID id, Long version) {
        return transition(id, InvoiceStatus.SENT, version);
    }

    @Transactional
    public InvoiceResponse cancel(UUID id, Long version) {
        return transition(id, InvoiceStatus.CANCELLED, version);
    }

    @Transactional
    public InvoiceResponse markPaid(UUID id, Long version) {
        return transition(id, InvoiceStatus.PAID, version);
    }

    private InvoiceResponse transition(UUID id, InvoiceStatus target, Long version) {
        User user = currentUserResolver.resolveUser();
        Invoice invoice = invoiceRepository
                .findByIdAndCreatedById(id, user.getId())
                .orElseThrow(() -> new InvoiceAppException("Invoice not found", HttpStatus.NOT_FOUND));

        if (version != null && !invoice.getVersion().equals(version)) {
            throw new ObjectOptimisticLockingFailureException(Invoice.class, id);
        }

        if (!invoice.getStatus().canTransitionTo(target)) {
            throw new InvoiceAppException(
                    String.format("Cannot transition invoice from %s to %s", invoice.getStatus(), target),
                    HttpStatus.UNPROCESSABLE_CONTENT
            );
        }

        if (target == InvoiceStatus.PAID) {
            BigDecimal alreadyPaid = paymentRepository.sumAmountByInvoiceId(invoice.getId());
            BigDecimal remaining = invoice.getTotal()
                    .subtract(alreadyPaid)
                    .setScale(4, RoundingMode.HALF_UP);

            if (remaining.compareTo(BigDecimal.ZERO) > 0) {
                String noteText = String.format("Recorded automatically. Remaining amount of %s marked as paid manually.", remaining);
                Payment compensatingPayment = Payment.builder()
                        .invoice(invoice)
                        .amount(remaining)
                        .method(MANUAL_MARK_PAID)
                        .notes(noteText)
                        .build();
                paymentRepository.save(compensatingPayment);
            }
        }

        InvoiceStatus oldStatus = invoice.getStatus();
        invoice.setStatus(target);

        String action = switch (target) {
            case SENT -> AuditAction.INVOICE_SENT;
            case PAID -> AuditAction.INVOICE_PAID;
            case OVERDUE -> AuditAction.INVOICE_OVERDUE;
            case CANCELLED -> AuditAction.INVOICE_CANCELLED;
            default -> INVOICE_STATUS_CHANGED;
        };

        auditService.log(
                INVOICE,
                invoice.getId(),
                action,
                Map.of("status", oldStatus.name()),
                Map.of("status", target.name()),
                user.getId()
        );

        notificationService.sendStatusChange(user.getId(), invoice.getInvoiceNumber(), invoice.getId().toString(), target.name());

        invoiceMetrics.recordStatusTransition(target);
        if (target == InvoiceStatus.SENT || target == InvoiceStatus.PAID || target == InvoiceStatus.CANCELLED) {
            double newBalance = computeTotalOutstandingBalance();
            invoiceMetrics.updateOutstandingBalance(newBalance);
        }

        return toResponse(invoice);
    }

    public DashboardStatsResponse getDashboardStats() {
        User user = currentUserResolver.resolveUser();
        BigDecimal totalInvoiced = invoiceRepository.computeTotalInvoiced(user.getId());
        BigDecimal outstanding = invoiceRepository.computeOutstandingBalance(user.getId());

        return new DashboardStatsResponse(totalInvoiced, outstanding);
    }

    private InvoiceResponse toResponse(Invoice invoice) {
        BigDecimal amountPaid = invoice.getPayments() == null
                ? BigDecimal.ZERO
                : invoice.getPayments().stream()
                  .map(Payment::getAmount)
                  .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal remaining = InvoiceStatus.CANCELLED.equals(invoice.getStatus())
                ? BigDecimal.ZERO
                : invoice.getTotal().subtract(amountPaid);

        List<LineItemResponse> lineItemResponses = invoice.getLineItems().stream()
                .map(li -> new LineItemResponse(
                        li.getId(),
                        li.getDescription(),
                        li.getQuantity(),
                        li.getUnitPrice(),
                        li.getDiscountPct(),
                        li.getLineTotal(),
                        li.getPosition()
                ))
                .toList();

        return new InvoiceResponse(
                invoice.getId(),
                invoice.getInvoiceNumber(),
                invoice.getClient().getName(),
                invoice.getClient().getId(),
                invoice.getStatus(),
                invoice.getIssueDate(),
                invoice.getDueDate(),
                invoice.getSubtotal(),
                invoice.getTaxRate(),
                invoice.getTaxAmount(),
                invoice.getTotal(),
                amountPaid,
                remaining,
                invoice.getNotes(),
                lineItemResponses,
                invoice.getCreatedAt(),
                invoice.getVersion()
        );
    }

    public double computeTotalOutstandingBalance() {
        BigDecimal total = invoiceRepository.computeOutstandingBalance();
        return total != null ? total.doubleValue() : 0.0;
    }

    private Map<String, Object> snapshotInvoiceState(Invoice invoice) {
        List<Map<String, Object>> lineItems = invoice.getLineItems().stream()
                .map(li -> Map.<String, Object>of(
                        "description", li.getDescription(),
                        "quantity", li.getQuantity().toString(),
                        "unitPrice", li.getUnitPrice().toString(),
                        "discountPct", li.getDiscountPct().toString(),
                        "lineTotal", li.getLineTotal().toString(),
                        "position", li.getPosition()
                )).toList();

        return Map.of(
                "invoiceNumber", invoice.getInvoiceNumber(),
                "clientId", invoice.getClient().getId().toString(),
                "status", invoice.getStatus().name(),
                "issueDate", invoice.getIssueDate().toString(),
                "dueDate", invoice.getDueDate().toString(),
                "taxRate", invoice.getTaxRate().toString(),
                "notes", invoice.getNotes() != null ? invoice.getNotes() : "",
                "lineItems", lineItems,
                "subtotal", invoice.getSubtotal().toString(),
                "total", invoice.getTotal().toString()
        );
    }
}