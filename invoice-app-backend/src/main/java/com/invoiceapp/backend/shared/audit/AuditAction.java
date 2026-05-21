package com.invoiceapp.backend.shared.audit;

public final class AuditAction {
    private AuditAction() {}

    public static final String INVOICE_CREATED = "INVOICE_CREATED";
    public static final String INVOICE_SENT = "INVOICE_SENT";
    public static final String INVOICE_PAID = "INVOICE_PAID";
    public static final String INVOICE_OVERDUE = "INVOICE_OVERDUE";
    public static final String INVOICE_CANCELLED = "INVOICE_CANCELLED";
    public static final String INVOICE_UPDATED = "INVOICE_UPDATED";

    public static final String PAYMENT_RECORDED = "PAYMENT_RECORDED";

    public static final String CLIENT_CREATED = "CLIENT_CREATED";
    public static final String CLIENT_UPDATED = "CLIENT_UPDATED";
    public static final String CLIENT_DELETED = "CLIENT_DELETED";

    public static final String USER_REGISTERED = "USER_REGISTERED";
    public static final String USER_LOGIN = "USER_LOGIN";
}