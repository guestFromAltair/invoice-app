package com.invoiceapp.backend.shared.reconciliation;

import com.invoiceapp.backend.invoice.domain.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReconciliationService Unit Tests")
class ReconciliationServiceTest {

    @Mock private InvoiceRepository invoiceRepository;
    @Mock private PaymentRepository paymentRepository;

    @InjectMocks
    private ReconciliationService reconciliationService;

    private Invoice buildInvoice(InvoiceStatus status, BigDecimal total, LocalDate issueDate, LocalDate dueDate) {
        return Invoice.builder()
                .id(UUID.randomUUID())
                .invoiceNumber("INV-TEST-" + UUID.randomUUID().toString().substring(0, 4))
                .status(status)
                .total(total)
                .subtotal(total)
                .taxRate(BigDecimal.ZERO)
                .taxAmount(BigDecimal.ZERO)
                .issueDate(issueDate)
                .dueDate(dueDate)
                .lineItems(new ArrayList<>())
                .payments(new ArrayList<>())
                .build();
    }

    @Nested
    @DisplayName("Ledger Mutation Discrepancy Diagnostics")
    class DiscrepancyLogic {

        @Test
        @DisplayName("should detect PAID invoice where payments are less than total")
        void should_detect_underpaid_paid_invoice() {
            Invoice invoice = buildInvoice(InvoiceStatus.PAID, new BigDecimal("1000.00"), LocalDate.now().minusDays(10), LocalDate.now().plusDays(20));

            when(invoiceRepository.streamAllActiveInvoicesForReconciliation()).thenReturn(Stream.of(invoice));
            when(invoiceRepository.streamStaleDraftsForReconciliation(any())).thenReturn(Stream.empty());
            when(paymentRepository.sumAmountGroupedByInvoice())
                    .thenReturn(List.of(new InvoicePaymentSum(invoice.getId(), new BigDecimal("800.00"))));

            ReconciliationService.ReconciliationReport report = reconciliationService.runReconciliation();

            assertThat(report.issueCount()).isEqualTo(1);
            assertThat(report.issues().getFirst().issueType()).isEqualTo("PAID_INVOICE_UNDERPAID");
            assertThat(report.issues().getFirst().discrepancy()).isEqualByComparingTo(new BigDecimal("200.00"));
        }

        @Test
        @DisplayName("should treat an invoice with no payments as zero")
        void should_treat_missing_payments_as_zero() {
            Invoice invoice = buildInvoice(InvoiceStatus.PAID, new BigDecimal("500.00"), LocalDate.now().minusDays(5), LocalDate.now().plusDays(10));

            when(invoiceRepository.streamAllActiveInvoicesForReconciliation()).thenReturn(Stream.of(invoice));
            when(invoiceRepository.streamStaleDraftsForReconciliation(any())).thenReturn(Stream.empty());
            when(paymentRepository.sumAmountGroupedByInvoice()).thenReturn(List.of());

            ReconciliationService.ReconciliationReport report = reconciliationService.runReconciliation();

            assertThat(report.issueCount()).isEqualTo(1);
            assertThat(report.issues().getFirst().paymentsTotal()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("should detect overpayments where ledger logs exceed active totals")
        void should_detect_overpayment() {
            Invoice invoice = buildInvoice(InvoiceStatus.SENT, new BigDecimal("500.00"), LocalDate.now().minusDays(5), LocalDate.now().plusDays(10));

            when(invoiceRepository.streamAllActiveInvoicesForReconciliation()).thenReturn(Stream.of(invoice));
            when(invoiceRepository.streamStaleDraftsForReconciliation(any())).thenReturn(Stream.empty());
            when(paymentRepository.sumAmountGroupedByInvoice())
                    .thenReturn(List.of(new InvoicePaymentSum(invoice.getId(), new BigDecimal("600.00"))));

            ReconciliationService.ReconciliationReport report = reconciliationService.runReconciliation();

            assertThat(report.issues()).anyMatch(i -> "OVERPAYMENT_DETECTED".equals(i.issueType()));
            assertThat(report.issues().getFirst().discrepancy()).isEqualByComparingTo(new BigDecimal("-100.00"));
        }

        @Test
        @DisplayName("should detect SENT status invoices that have breached their due dates")
        void should_detect_sent_invoice_past_due_date() {
            Invoice invoice = buildInvoice(InvoiceStatus.SENT, new BigDecimal("1000.00"), LocalDate.now().minusDays(40), LocalDate.now().minusDays(5));

            when(invoiceRepository.streamAllActiveInvoicesForReconciliation()).thenReturn(Stream.of(invoice));
            when(invoiceRepository.streamStaleDraftsForReconciliation(any())).thenReturn(Stream.empty());
            when(paymentRepository.sumAmountGroupedByInvoice())
                    .thenReturn(List.of(new InvoicePaymentSum(invoice.getId(), BigDecimal.ZERO)));

            ReconciliationService.ReconciliationReport report = reconciliationService.runReconciliation();

            assertThat(report.issues()).anyMatch(i -> "SENT_INVOICE_OVERDUE".equals(i.issueType()));
        }

        @Test
        @DisplayName("should capture stale DRAFT invoices processed from the stale stream container")
        void should_detect_stale_draft_invoices() {
            Invoice draftInvoice = buildInvoice(InvoiceStatus.DRAFT, new BigDecimal("1200.00"), LocalDate.now().minusDays(95), LocalDate.now().minusDays(65));

            when(invoiceRepository.streamAllActiveInvoicesForReconciliation()).thenReturn(Stream.empty());
            when(invoiceRepository.streamStaleDraftsForReconciliation(any())).thenReturn(Stream.of(draftInvoice));

            ReconciliationService.ReconciliationReport report = reconciliationService.runReconciliation();

            assertThat(report.issueCount()).isEqualTo(1);
            assertThat(report.issues().getFirst().issueType()).isEqualTo("STALE_DRAFT_INVOICE");
        }

        @Test
        @DisplayName("should return a clean report when all invoices perfectly align")
        void should_return_clean_report_when_no_issues() {
            Invoice invoice = buildInvoice(InvoiceStatus.PAID, new BigDecimal("1000.00"), LocalDate.now().minusDays(5), LocalDate.now().plusDays(25));

            when(invoiceRepository.streamAllActiveInvoicesForReconciliation()).thenReturn(Stream.of(invoice));
            when(invoiceRepository.streamStaleDraftsForReconciliation(any())).thenReturn(Stream.empty());
            when(paymentRepository.sumAmountGroupedByInvoice())
                    .thenReturn(List.of(new InvoicePaymentSum(invoice.getId(), new BigDecimal("1000.00"))));

            ReconciliationService.ReconciliationReport report = reconciliationService.runReconciliation();

            assertThat(report.issueCount()).isZero();
            assertThat(report.summary()).contains("successfully");
        }

        @Test
        @DisplayName("should query payment sums once, not once per invoice")
        void should_query_payment_sums_once() {
            Invoice first = buildInvoice(InvoiceStatus.PAID, new BigDecimal("100.00"), LocalDate.now().minusDays(5), LocalDate.now().plusDays(10));
            Invoice second = buildInvoice(InvoiceStatus.PAID, new BigDecimal("200.00"), LocalDate.now().minusDays(5), LocalDate.now().plusDays(10));

            when(invoiceRepository.streamAllActiveInvoicesForReconciliation()).thenReturn(Stream.of(first, second));
            when(invoiceRepository.streamStaleDraftsForReconciliation(any())).thenReturn(Stream.empty());
            when(paymentRepository.sumAmountGroupedByInvoice()).thenReturn(List.of(
                    new InvoicePaymentSum(first.getId(), new BigDecimal("100.00")),
                    new InvoicePaymentSum(second.getId(), new BigDecimal("200.00"))
            ));

            ReconciliationService.ReconciliationReport report = reconciliationService.runReconciliation();

            assertThat(report.totalInvoicesChecked()).isEqualTo(2);
            assertThat(report.issueCount()).isZero();
            verify(paymentRepository, times(1)).sumAmountGroupedByInvoice();
            verify(paymentRepository, never()).sumAmountByInvoiceId(any());
        }
    }

    @Nested
    @DisplayName("Scheduler Trigger Operations")
    class SchedulerIntegration {

        @Test
        @DisplayName("should execute automated cron sequence wrapper cleanly without throwing exceptions")
        void scheduledReconciliation_runs_successfully() {
            when(invoiceRepository.streamAllActiveInvoicesForReconciliation()).thenReturn(Stream.empty());
            when(invoiceRepository.streamStaleDraftsForReconciliation(any())).thenReturn(Stream.empty());

            reconciliationService.scheduledReconciliation();

            verify(invoiceRepository, times(1)).streamAllActiveInvoicesForReconciliation();
            verify(invoiceRepository, times(1)).streamStaleDraftsForReconciliation(any());
        }
    }
}