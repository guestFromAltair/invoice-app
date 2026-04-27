// src/main/java/com/invoiceapp/backend/invoice/service/PaymentService.java
package com.invoiceapp.backend.invoice.service;

import com.invoiceapp.backend.auth.domain.User;
import com.invoiceapp.backend.auth.domain.UserRepository;
import com.invoiceapp.backend.invoice.domain.*;
import com.invoiceapp.backend.shared.exception.InvoiceAppException;
import com.invoiceapp.backend.shared.metrics.InvoiceMetrics;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final InvoiceRepository invoiceRepository;
    private final UserRepository userRepository;
    private final InvoiceService invoiceService;
    private final InvoiceMetrics invoiceMetrics;

    public record PaymentRequest(
            BigDecimal amount,
            Instant paidAt,
            String method,
            String notes
    ) {}

    public record PaymentResponse(
            UUID id,
            BigDecimal amount,
            Instant paidAt,
            String method,
            String notes,
            String createdAt
    ) {}

    private User getCurrentUser() {
        String email = Objects.requireNonNull(
                SecurityContextHolder.getContext().getAuthentication()
        ).getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new InvoiceAppException(
                        "Authenticated user not found",
                        HttpStatus.INTERNAL_SERVER_ERROR
                ));
    }

    @Transactional
    public PaymentResponse recordPayment(UUID invoiceId, PaymentRequest request) {
        User user = getCurrentUser();

        Invoice invoice = invoiceRepository
                .findByIdAndCreatedById(invoiceId, user.getId())
                .orElseThrow(() -> new InvoiceAppException(
                        "Invoice not found", HttpStatus.NOT_FOUND
                ));

        if (invoice.getStatus() != InvoiceStatus.SENT && invoice.getStatus() != InvoiceStatus.OVERDUE) {
            throw new InvoiceAppException(
                    "Payments can only be recorded against SENT or OVERDUE invoices",
                    HttpStatus.UNPROCESSABLE_CONTENT
            );
        }

        BigDecimal alreadyPaid = paymentRepository.sumAmountByInvoiceId(invoiceId);

        BigDecimal remaining = invoice.getTotal()
                .subtract(alreadyPaid)
                .setScale(4, RoundingMode.HALF_UP);

        if (request.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvoiceAppException(
                    "Payment amount must be greater than zero",
                    HttpStatus.UNPROCESSABLE_CONTENT
            );
        }

        if (request.amount().compareTo(remaining) > 0) {
            throw new InvoiceAppException(
                    String.format(
                            "Payment amount %.2f exceeds remaining balance %.2f",
                            request.amount(), remaining
                    ),
                    HttpStatus.UNPROCESSABLE_CONTENT
            );
        }

        Payment payment = Payment.builder()
                .invoice(invoice)
                .amount(request.amount().setScale(4, RoundingMode.HALF_UP))
                .paidAt(request.paidAt() != null ? request.paidAt() : Instant.now())
                .method(request.method())
                .notes(request.notes())
                .build();

        Payment saved = paymentRepository.save(payment);

        BigDecimal newAmountPaid = alreadyPaid.add(request.amount())
                .setScale(4, RoundingMode.HALF_UP);

        BigDecimal newRemaining = invoice.getTotal()
                .subtract(newAmountPaid)
                .setScale(4, RoundingMode.HALF_UP);

        invoice.setStatus(newRemaining.compareTo(BigDecimal.ZERO) == 0 ? InvoiceStatus.PAID : invoice.getStatus());
        invoiceRepository.saveAndFlush(invoice);

        double newBalance = invoiceService.computeTotalOutstandingBalance();
        invoiceMetrics.updateOutstandingBalance(newBalance);

        return toResponse(saved);
    }

    public List<PaymentResponse> findAllByInvoice(UUID invoiceId) {
        User user = getCurrentUser();

        invoiceRepository.findByIdAndCreatedById(invoiceId, user.getId())
                .orElseThrow(() -> new InvoiceAppException(
                        "Invoice not found", HttpStatus.NOT_FOUND
                ));

        return paymentRepository.findAllByInvoiceId(invoiceId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private PaymentResponse toResponse(Payment payment) {
        return new PaymentResponse(
                payment.getId(),
                payment.getAmount(),
                payment.getPaidAt(),
                payment.getMethod(),
                payment.getNotes(),
                payment.getCreatedAt().toString()
        );
    }
}