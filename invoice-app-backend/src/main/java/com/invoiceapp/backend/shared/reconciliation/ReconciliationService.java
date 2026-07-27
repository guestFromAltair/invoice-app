package com.invoiceapp.backend.shared.reconciliation;

import com.invoiceapp.backend.invoice.domain.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReconciliationService {

    private final InvoiceRepository invoiceRepository;
    private final PaymentRepository paymentRepository;

    private static final String PAID_INVOICE_UNDERPAID = "PAID_INVOICE_UNDERPAID";
    private static final String OVERPAYMENT_DETECTED = "OVERPAYMENT_DETECTED";
    private static final String SENT_INVOICE_OVERDUE = "SENT_INVOICE_OVERDUE";
    private static final String STALE_DRAFT_INVOICE = "STALE_DRAFT_INVOICE";

    public record ReconciliationIssue(
            String issueType,
            String invoiceNumber,
            String description,
            BigDecimal invoiceTotal,
            BigDecimal paymentsTotal,
            BigDecimal discrepancy,
            String status
    ) {}

    public record ReconciliationReport(
            LocalDate reportDate,
            int totalInvoicesChecked,
            int issueCount,
            List<ReconciliationIssue> issues,
            String summary
    ) {}

    @Transactional(readOnly = true)
    public ReconciliationReport runReconciliation() {
        List<ReconciliationIssue> issues = new ArrayList<>();
        AtomicInteger checkedCount = new AtomicInteger();

        Map<UUID, BigDecimal> paymentsByInvoice = paymentRepository.sumAmountGroupedByInvoice()
                .stream()
                .collect(Collectors.toMap(InvoicePaymentSum::invoiceId, InvoicePaymentSum::total));

        try (Stream<Invoice> invoiceStream = invoiceRepository.streamAllActiveInvoicesForReconciliation()) {
            invoiceStream.forEach(invoice -> {
                checkedCount.incrementAndGet();

                BigDecimal paymentsTotal = paymentsByInvoice.getOrDefault(invoice.getId(), BigDecimal.ZERO);
                BigDecimal discrepancy = invoice.getTotal().subtract(paymentsTotal).setScale(4, RoundingMode.HALF_UP);

                if (invoice.getStatus() == InvoiceStatus.PAID && discrepancy.compareTo(BigDecimal.ZERO) > 0) {
                    issues.add(new ReconciliationIssue(
                            PAID_INVOICE_UNDERPAID,
                            invoice.getInvoiceNumber(),
                            String.format("Invoice is marked PAID but payments sum (%.2f) is less than invoice total (%.2f). Discrepancy: %.2f",
                                    paymentsTotal, invoice.getTotal(), discrepancy),
                            invoice.getTotal(), paymentsTotal, discrepancy, invoice.getStatus().name()
                    ));
                }

                if (discrepancy.compareTo(BigDecimal.ZERO) < 0) {
                    BigDecimal overpayment = discrepancy.abs();
                    issues.add(new ReconciliationIssue(
                            OVERPAYMENT_DETECTED,
                            invoice.getInvoiceNumber(),
                            String.format("Payments (%.2f) exceed invoice total (%.2f). Overpayment: %.2f",
                                    paymentsTotal, invoice.getTotal(), overpayment),
                            invoice.getTotal(), paymentsTotal, overpayment.negate(), invoice.getStatus().name()
                    ));
                }

                if (invoice.getStatus() == InvoiceStatus.SENT && invoice.getDueDate().isBefore(LocalDate.now())) {
                    issues.add(new ReconciliationIssue(
                            SENT_INVOICE_OVERDUE,
                            invoice.getInvoiceNumber(),
                            String.format("Invoice was due on %s but is still in SENT status. Overdue detection scheduler may have failed.",
                                    invoice.getDueDate()),
                            invoice.getTotal(), paymentsTotal, discrepancy, invoice.getStatus().name()
                    ));
                }
            });
        }

        LocalDate ninetyDaysAgo = LocalDate.now().minusDays(90);
        try (Stream<Invoice> draftStream = invoiceRepository.streamStaleDraftsForReconciliation(ninetyDaysAgo)) {
            draftStream.forEach(draft -> {
                checkedCount.incrementAndGet();
                issues.add(new ReconciliationIssue(
                        STALE_DRAFT_INVOICE,
                        draft.getInvoiceNumber(),
                        String.format("Invoice has been in DRAFT status since %s (%d days). Consider cancelling or completing it.",
                                draft.getIssueDate(),
                                LocalDate.now().toEpochDay() - draft.getIssueDate().toEpochDay()),
                        draft.getTotal(),
                        BigDecimal.ZERO,
                        draft.getTotal(),
                        draft.getStatus().name()
                ));
            });
        }

        int totalChecked = checkedCount.get();

        String summary = issues.isEmpty()
                ? String.format("✅ All %d invoices reconciled successfully. No issues found.", totalChecked)
                : String.format("⚠️ Found %d issue(s) across %d invoices checked.", issues.size(), totalChecked);

        log.info("Reconciliation complete: {} issues found in {} invoices", issues.size(), totalChecked);

        if (!issues.isEmpty()) {
            issues.forEach(issue ->
                    log.warn("RECONCILIATION ISSUE [{}]: {} — {}",
                            issue.issueType(), issue.invoiceNumber(), issue.description())
            );
        }

        return new ReconciliationReport(
                LocalDate.now(),
                totalChecked,
                issues.size(),
                issues,
                summary
        );
    }

    @Scheduled(cron = "0 0 2 * * *")
    public void scheduledReconciliation() {
        log.info("Starting scheduled reconciliation...");
        ReconciliationReport report = runReconciliation();
        log.info("Scheduled reconciliation complete: {}", report.summary());
    }
}