package com.invoiceapp.backend.invoice.scheduler;

import com.invoiceapp.backend.invoice.domain.Invoice;
import com.invoiceapp.backend.invoice.domain.InvoiceRepository;
import com.invoiceapp.backend.invoice.domain.InvoiceStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OverdueDetectionJob")
class OverdueDetectionJobTest {

    @Mock
    private InvoiceRepository invoiceRepository;

    @InjectMocks
    private OverdueDetectionJob overdueDetectionJob;

    @Test
    @DisplayName("should mark SENT invoices as OVERDUE when past due date")
    void should_mark_sent_invoices_as_overdue() {
        Invoice overdueInvoice = Invoice.builder()
                .id(UUID.randomUUID())
                .invoiceNumber("INV-2024-00001")
                .status(InvoiceStatus.SENT)
                .issueDate(LocalDate.now().minusDays(60))
                .dueDate(LocalDate.now().minusDays(1))
                .subtotal(BigDecimal.TEN)
                .taxRate(BigDecimal.ZERO)
                .taxAmount(BigDecimal.ZERO)
                .total(BigDecimal.TEN)
                .build();

        when(invoiceRepository.findAllOverdue(any(LocalDate.class))).thenReturn(List.of(overdueInvoice));

        overdueDetectionJob.detectOverdueInvoices();

        assertThat(overdueInvoice.getStatus()).isEqualTo(InvoiceStatus.OVERDUE);
    }

    @Test
    @DisplayName("should process multiple overdue invoices in one job run")
    void should_process_multiple_overdue_invoices() {
        List<Invoice> overdueInvoices = List.of(
                buildOverdueInvoice("INV-2024-00001"),
                buildOverdueInvoice("INV-2024-00002"),
                buildOverdueInvoice("INV-2024-00003")
        );

        when(invoiceRepository.findAllOverdue(any(LocalDate.class))).thenReturn(overdueInvoices);

        overdueDetectionJob.detectOverdueInvoices();

        overdueInvoices.forEach(invoice ->
                assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.OVERDUE)
        );
    }

    @Test
    @DisplayName("should do nothing when no overdue invoices exist")
    void should_do_nothing_when_no_overdue_invoices() {
        when(invoiceRepository.findAllOverdue(any(LocalDate.class))).thenReturn(List.of());

        overdueDetectionJob.detectOverdueInvoices();

        verifyNoMoreInteractions(invoiceRepository);
    }

    @Test
    @DisplayName("should query with today's date")
    void should_query_with_todays_date() {
        when(invoiceRepository.findAllOverdue(any())).thenReturn(List.of());

        overdueDetectionJob.detectOverdueInvoices();

        ArgumentCaptor<LocalDate> dateCaptor = ArgumentCaptor.forClass(LocalDate.class);
        verify(invoiceRepository).findAllOverdue(dateCaptor.capture());

        assertThat(dateCaptor.getValue()).isEqualTo(LocalDate.now());
    }

    private Invoice buildOverdueInvoice(String invoiceNumber) {
        return Invoice.builder()
                .id(UUID.randomUUID())
                .invoiceNumber(invoiceNumber)
                .status(InvoiceStatus.SENT)
                .issueDate(LocalDate.now().minusDays(60))
                .dueDate(LocalDate.now().minusDays(1))
                .subtotal(BigDecimal.TEN)
                .taxRate(BigDecimal.ZERO)
                .taxAmount(BigDecimal.ZERO)
                .total(BigDecimal.TEN)
                .build();
    }
}