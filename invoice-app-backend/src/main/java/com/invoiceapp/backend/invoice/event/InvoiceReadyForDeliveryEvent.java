package com.invoiceapp.backend.invoice.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record InvoiceReadyForDeliveryEvent(
        UUID invoiceId,
        String invoiceNumber,
        UUID ownerId,
        String status,
        Recipient recipient,
        LocalDate issueDate,
        LocalDate dueDate,
        BigDecimal subtotal,
        BigDecimal taxRate,
        BigDecimal taxAmount,
        BigDecimal total,
        String notes,
        List<Line> lineItems,
        Instant occurredAt
) {
    public record Recipient(String name, String email, String address, String vatNumber) {}

    public record Line(
            String description,
            BigDecimal quantity,
            BigDecimal unitPrice,
            BigDecimal discountPct,
            BigDecimal lineTotal,
            Integer position
    ) {}
}