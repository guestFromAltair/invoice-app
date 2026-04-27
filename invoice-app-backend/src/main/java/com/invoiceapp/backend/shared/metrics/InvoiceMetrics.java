package com.invoiceapp.backend.shared.metrics;

import com.invoiceapp.backend.invoice.domain.InvoiceRepository;
import com.invoiceapp.backend.invoice.domain.InvoiceStatus;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicReference;

@Component
@Slf4j
public class InvoiceMetrics {

    private final Counter invoicesCreatedCounter;
    private final Counter invoicesSentCounter;
    private final Counter invoicesPaidCounter;
    private final Counter invoicesCancelledCounter;

    private final AtomicReference<Double> outstandingBalance = new AtomicReference<>(0.0);

    public InvoiceMetrics(MeterRegistry registry, InvoiceRepository invoiceRepository) {
        this.invoicesCreatedCounter = Counter.builder("invoices.created")
                .description("Total number of invoices created")
                .tag("type", "invoice")
                .register(registry);

        this.invoicesSentCounter = Counter.builder("invoices.status.transitions")
                .description("Invoice status transitions")
                .tag("to_status", "SENT")
                .register(registry);

        this.invoicesPaidCounter = Counter.builder("invoices.status.transitions")
                .description("Invoice status transitions")
                .tag("to_status", "PAID")
                .register(registry);

        this.invoicesCancelledCounter = Counter.builder("invoices.status.transitions")
                .description("Invoice status transitions")
                .tag("to_status", "CANCELLED")
                .register(registry);

        Gauge.builder("invoices.outstanding.balance", outstandingBalance, AtomicReference::get)
                .description("Total outstanding invoice balance")
                .tag("currency", "EUR")
                .register(registry);

        log.info("Invoice metrics registered successfully");
    }

    public void recordInvoiceCreated() {
        invoicesCreatedCounter.increment();
    }

    public void recordStatusTransition(InvoiceStatus toStatus) {
        switch (toStatus) {
            case SENT      -> invoicesSentCounter.increment();
            case PAID      -> invoicesPaidCounter.increment();
            case CANCELLED -> invoicesCancelledCounter.increment();
            default        -> {}
        }
    }

    public void updateOutstandingBalance(double balance) {
        outstandingBalance.set(balance);
    }
}