package com.invoiceapp.backend.invoice.event;

import java.time.Instant;
import java.util.UUID;

public record InvoiceStatusChangedEvent(
        UUID invoiceId,
        String invoiceNumber,
        String oldStatus,
        String newStatus,
        UUID changedBy,
        UUID ownerId,
        Instant occurredAt
) {}