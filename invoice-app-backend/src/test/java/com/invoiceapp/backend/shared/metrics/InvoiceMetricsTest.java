package com.invoiceapp.backend.shared.metrics;

import com.invoiceapp.backend.invoice.domain.InvoiceRepository;
import com.invoiceapp.backend.invoice.domain.InvoiceStatus;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
@DisplayName("InvoiceMetrics")
class InvoiceMetricsTest {

    private InvoiceMetrics invoiceMetrics;
    private MeterRegistry meterRegistry;

    @Mock
    private InvoiceRepository invoiceRepository;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        invoiceMetrics = new InvoiceMetrics(meterRegistry, invoiceRepository);
    }

    @Test
    @DisplayName("recordStatusTransition should increment correct counter for SENT")
    void record_status_transition_sent() {
        invoiceMetrics.recordStatusTransition(InvoiceStatus.SENT);

        double count = meterRegistry.get("invoices.status.transitions")
                .tag("to_status", "SENT")
                .counter()
                .count();

        assertThat(count).isEqualTo(1.0);
    }

    @Test
    @DisplayName("recordStatusTransition should increment correct counter for PAID")
    void record_status_transition_paid() {
        invoiceMetrics.recordStatusTransition(InvoiceStatus.PAID);

        double count = meterRegistry.get("invoices.status.transitions")
                .tag("to_status", "PAID")
                .counter()
                .count();

        assertThat(count).isEqualTo(1.0);
    }

    @Test
    @DisplayName("recordStatusTransition should increment correct counter for CANCELLED")
    void record_status_transition_cancelled() {
        invoiceMetrics.recordStatusTransition(InvoiceStatus.CANCELLED);

        double count = meterRegistry.get("invoices.status.transitions")
                .tag("to_status", "CANCELLED")
                .counter()
                .count();

        assertThat(count).isEqualTo(1.0);
    }

    @Test
    @DisplayName("recordStatusTransition should do nothing for DRAFT")
    void record_status_transition_draft_no_op() {
        invoiceMetrics.recordStatusTransition(InvoiceStatus.DRAFT);

        List<Counter> counters = (List<Counter>) meterRegistry.find("invoices.status.transitions").counters();

        assertThat(counters).hasSize(3);

        for (Counter counter : counters) {
            assertThat(counter.count()).isEqualTo(0.0);
        }
    }

    @Test
    @DisplayName("updateOutstandingBalance should update the gauge value")
    void update_outstanding_balance_updates_gauge() {
        double expectedBalance = 5500.50;
        invoiceMetrics.updateOutstandingBalance(expectedBalance);

        double gaugeValue = meterRegistry.get("invoices.outstanding.balance")
                .tag("currency", "EUR")
                .gauge()
                .value();

        assertThat(gaugeValue).isEqualTo(expectedBalance);
    }

    @Test
    @DisplayName("recordInvoiceCreated should increment the creation counter")
    void record_invoice_created_increments_counter() {
        invoiceMetrics.recordInvoiceCreated();
        invoiceMetrics.recordInvoiceCreated();

        double count = meterRegistry.get("invoices.created")
                .tag("type", "invoice")
                .counter()
                .count();

        assertThat(count).isEqualTo(2.0);
    }

    @Test
    @DisplayName("Constructor should initialize gauge at zero")
    void initial_gauge_value_is_zero() {
        double gaugeValue = meterRegistry.get("invoices.outstanding.balance")
                .tag("currency", "EUR")
                .gauge()
                .value();

        assertThat(gaugeValue).isEqualTo(0.0);
    }
}