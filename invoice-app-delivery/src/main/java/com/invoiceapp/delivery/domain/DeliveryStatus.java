package com.invoiceapp.delivery.domain;

public enum DeliveryStatus {
    PENDING,     // row created, not yet attempted
    SENT,        // delivered
    FAILED,      // transient failure, will retry
    ABANDONED    // gave up: too many attempts, or a problem retrying won't fix
}