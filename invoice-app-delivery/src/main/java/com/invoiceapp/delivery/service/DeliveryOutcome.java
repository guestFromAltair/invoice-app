package com.invoiceapp.delivery.service;

import com.invoiceapp.delivery.domain.DeliveryStatus;

import java.time.Instant;

public record DeliveryOutcome(
        DeliveryStatus status,
        int attempts,
        String error,
        Instant nextAttemptAt
) {

    public static DeliveryOutcome sent(int attempts) {
        return new DeliveryOutcome(DeliveryStatus.SENT, attempts, null, null);
    }

    public static DeliveryOutcome retry(int attempts, String error, Instant nextAttemptAt) {
        return new DeliveryOutcome(DeliveryStatus.FAILED, attempts, error, nextAttemptAt);
    }

    public static DeliveryOutcome abandoned(int attempts, String error) {
        return new DeliveryOutcome(DeliveryStatus.ABANDONED, attempts, error, null);
    }
}