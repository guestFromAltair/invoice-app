package com.invoiceapp.backend.invoice.service;

import com.invoiceapp.backend.auth.domain.User;
import com.invoiceapp.backend.auth.domain.UserRepository;
import com.invoiceapp.backend.client.domain.Client;
import com.invoiceapp.backend.client.domain.ClientRepository;
import com.invoiceapp.backend.invoice.domain.*;
import com.invoiceapp.backend.notification.service.NotificationService;
import com.invoiceapp.backend.shared.exception.InvoiceAppException;
import com.invoiceapp.backend.shared.metrics.InvoiceMetrics;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Year;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InvoiceService {

    private final InvoiceRepository invoiceRepository;
    private final PaymentRepository paymentRepository;
    private final ClientRepository clientRepository;
    private final NotificationService notificationService;
    private final UserRepository userRepository;
    private final InvoiceMetrics invoiceMetrics;

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
            Instant createdAt
    ) {}

    private User getCurrentUser() {
        String email = Objects.requireNonNull(SecurityContextHolder.getContext()
                .getAuthentication()).getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new InvoiceAppException(
                        "Authenticated user not found",
                        HttpStatus.INTERNAL_SERVER_ERROR
                ));
    }

    private String generateInvoiceNumber() {
        Long seq = invoiceRepository.nextInvoiceSequence();
        int year = Year.now().getValue();
        return String.format("INV-%d-%05d", year, seq);
    }

    @Transactional
    public InvoiceResponse create(InvoiceRequest request) {
        User user = getCurrentUser();

        Client client = clientRepository
                .findByIdAndOwnerId(request.clientId(), user.getId())
                .orElseThrow(() -> new InvoiceAppException(
                        "Client not found", HttpStatus.NOT_FOUND
                ));

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
        invoiceMetrics.recordInvoiceCreated();
        return toResponse(saved);
    }

    public Page<InvoiceResponse> findAll(InvoiceStatus status, UUID clientId, Pageable pageable) {
        User user = getCurrentUser();
        return invoiceRepository
                .findAllByFilters(user.getId(), status, clientId, pageable)
                .map(this::toResponse);
    }

    public InvoiceResponse findById(UUID id) {
        User user = getCurrentUser();
        Invoice invoice = invoiceRepository
                .findByIdAndCreatedById(id, user.getId())
                .orElseThrow(() -> new InvoiceAppException(
                        "Invoice not found", HttpStatus.NOT_FOUND
                ));
        return toResponse(invoice);
    }

    @Transactional
    public InvoiceResponse updateLineItems(UUID id, List<LineItemRequest> lineItems) {
        User user = getCurrentUser();
        Invoice invoice = invoiceRepository
                .findByIdAndCreatedById(id, user.getId())
                .orElseThrow(() -> new InvoiceAppException(
                        "Invoice not found", HttpStatus.NOT_FOUND
                ));

        if (invoice.getStatus() != InvoiceStatus.DRAFT) {
            throw new InvoiceAppException(
                    "Only DRAFT invoices can be edited",
                    HttpStatus.UNPROCESSABLE_CONTENT
            );
        }

        // Hibernate issues delete current LineItems first upon commit(before we create a new list of LineItems)
        invoice.getLineItems().clear();

        List<LineItem> newItems = lineItems.stream()
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

        // Now that the current items have been deleted we create a new (updated) list of LineItems
        invoice.getLineItems().addAll(newItems);
        invoice.recalculateTotals();
        return toResponse(invoice);
    }

    @Transactional
    public InvoiceResponse send(UUID id) {
        return transition(id, InvoiceStatus.SENT);
    }

    @Transactional
    public InvoiceResponse cancel(UUID id) {
        return transition(id, InvoiceStatus.CANCELLED);
    }

    @Transactional
    public InvoiceResponse markOverdue(UUID id) {
        return transition(id, InvoiceStatus.OVERDUE);
    }

    @Transactional
    public InvoiceResponse markPaid(UUID id) {
        return transition(id, InvoiceStatus.PAID);
    }

    private InvoiceResponse transition(UUID id, InvoiceStatus target) {
        User user = getCurrentUser();
        Invoice invoice = invoiceRepository
                .findByIdAndCreatedById(id, user.getId())
                .orElseThrow(() -> new InvoiceAppException(
                        "Invoice not found", HttpStatus.NOT_FOUND
                ));

        if (!invoice.getStatus().canTransitionTo(target)) {
            throw new InvoiceAppException(
                    String.format(
                            "Cannot transition invoice from %s to %s",
                            invoice.getStatus(), target
                    ),
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
                        .method("MANUAL_MARK_PAID")
                        .notes(noteText)
                        .build();
                paymentRepository.save(compensatingPayment);
            }
        }

        invoice.setStatus(target);

        notificationService.sendStatusChange(user.getId(), invoice.getInvoiceNumber(), invoice.getId().toString(), target.name());

        invoiceMetrics.recordStatusTransition(target);
        if (target == InvoiceStatus.SENT || target == InvoiceStatus.PAID || target == InvoiceStatus.CANCELLED) {
            double newBalance = computeTotalOutstandingBalance();
            invoiceMetrics.updateOutstandingBalance(newBalance);
        }

        return toResponse(invoice);
    }

    private InvoiceResponse toResponse(Invoice invoice) {
        BigDecimal amountPaid = invoice.getPayments() == null
                ? BigDecimal.ZERO
                : invoice.getPayments().stream()
                  .map(Payment::getAmount)
                  .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal remaining = invoice.getTotal().subtract(amountPaid);

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
                invoice.getCreatedAt()
        );
    }

    public double computeTotalOutstandingBalance() {
        BigDecimal total = invoiceRepository.computeOutstandingBalance();
        return total != null ? total.doubleValue() : 0.0;
    }
}