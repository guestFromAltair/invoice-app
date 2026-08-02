package com.invoiceapp.delivery.pdf;

import com.invoiceapp.delivery.InvoicePdfRenderer;
import com.invoiceapp.delivery.event.InvoiceReadyForDeliveryEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("InvoicePdfRenderer")
class InvoicePdfRendererTest {

    private final InvoicePdfRenderer renderer = new InvoicePdfRenderer();

    @Test
    @DisplayName("renders a PDF from the event alone")
    void renders() throws Exception {
        byte[] pdf = renderer.render(sampleEvent());

        assertThat(pdf).isNotEmpty();
        assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");
    }

    @Test
    @DisplayName("handles optional fields being null")
    void handles_nulls() throws Exception {
        var e = sampleEvent();
        var bare = new InvoiceReadyForDeliveryEvent(
                e.invoiceId(), e.invoiceNumber(), e.ownerId(), e.status(),
                new InvoiceReadyForDeliveryEvent.Recipient("Acme", "a@acme.com", null, null),
                e.issueDate(), e.dueDate(), e.subtotal(), e.taxRate(), e.taxAmount(),
                e.total(), null, e.lineItems(), e.occurredAt());

        assertThat(renderer.render(bare)).isNotEmpty();
    }

    private InvoiceReadyForDeliveryEvent sampleEvent() {
        return new InvoiceReadyForDeliveryEvent(
                UUID.randomUUID(), "INV-2026-00001", UUID.randomUUID(), "SENT",
                new InvoiceReadyForDeliveryEvent.Recipient(
                        "Acme Corp", "billing@acme.com", "12 Rue de Rivoli, Paris", "FR123"),
                LocalDate.of(2026, 1, 15), LocalDate.of(2026, 2, 15),
                new BigDecimal("1500.0000"), new BigDecimal("0.2000"),
                new BigDecimal("300.0000"), new BigDecimal("1800.0000"),
                "Web development services",
                List.of(new InvoiceReadyForDeliveryEvent.Line(
                        "Frontend development", new BigDecimal("10"), new BigDecimal("150.00"),
                        BigDecimal.ZERO, new BigDecimal("1500.0000"), 1)),
                Instant.now()
        );
    }
}