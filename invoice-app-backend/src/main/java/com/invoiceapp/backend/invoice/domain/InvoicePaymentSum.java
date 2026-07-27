package com.invoiceapp.backend.invoice.domain;

import java.math.BigDecimal;
import java.util.UUID;

public record InvoicePaymentSum(UUID invoiceId, BigDecimal total) {}