package com.invoiceapp.backend.invoice.controller;

import com.invoiceapp.backend.invoice.service.PaymentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/invoices/{invoiceId}/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @GetMapping
    public List<PaymentService.PaymentResponse> findAll(@PathVariable UUID invoiceId) {
        return paymentService.findAllByInvoice(invoiceId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentService.PaymentResponse recordPayment(
            @PathVariable UUID invoiceId,
            @Valid @RequestBody PaymentRequest request
    ) {
        return paymentService.recordPayment(
                invoiceId,
                new PaymentService.PaymentRequest(
                        request.amount(),
                        request.paidAt(),
                        request.method(),
                        request.notes()
                )
        );
    }

    public record PaymentRequest(
            @NotNull(message = "Amount is required")
            @Positive(message = "Amount must be greater than zero")
            BigDecimal amount,
            Instant paidAt,
            String method,
            String notes
    ) {}
}