package com.invoiceapp.backend.invoice.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentRecordedEvent(
        UUID paymentId,
        UUID invoiceId,
        BigDecimal amount,
        String method,
        Instant paidAt,
        String invoiceStatus,
        Instant occurredAt
) {}