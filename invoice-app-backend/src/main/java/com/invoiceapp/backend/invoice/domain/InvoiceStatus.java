package com.invoiceapp.backend.invoice.domain;

import java.util.Set;

public enum InvoiceStatus {
    DRAFT,
    SENT,
    PAID,
    OVERDUE,
    CANCELLED;

    public Set<InvoiceStatus> allowedTransitions() {
        return switch (this) {
            case DRAFT     -> Set.of(SENT, CANCELLED);
            case SENT      -> Set.of(PAID, OVERDUE, CANCELLED);
            case OVERDUE   -> Set.of(PAID, CANCELLED);
            case PAID, CANCELLED -> Set.of();
        };
    }

    public boolean canTransitionTo(InvoiceStatus target) {
        return allowedTransitions().contains(target);
    }
}