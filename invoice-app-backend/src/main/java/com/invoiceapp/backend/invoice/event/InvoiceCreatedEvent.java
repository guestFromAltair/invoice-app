package com.invoiceapp.backend.invoice.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record InvoiceCreatedEvent(
        UUID invoiceId,
        String invoiceNumber,
        UUID clientId,
        UUID createdBy,
        String status,
        BigDecimal total,
        Instant occurredAt
) {}