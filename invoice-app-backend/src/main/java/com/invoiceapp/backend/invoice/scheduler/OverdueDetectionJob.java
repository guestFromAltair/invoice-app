package com.invoiceapp.backend.invoice.scheduler;

import com.invoiceapp.backend.invoice.domain.Invoice;
import com.invoiceapp.backend.invoice.domain.InvoiceRepository;
import com.invoiceapp.backend.invoice.domain.InvoiceStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class OverdueDetectionJob {

    private final InvoiceRepository invoiceRepository;

    @Scheduled(cron = "0 0 1 * * *")
    @Transactional
    public void detectOverdueInvoices() {
        LocalDate today = LocalDate.now();
        List<Invoice> overdueInvoices = invoiceRepository.findAllOverdue(today);

        if (overdueInvoices.isEmpty()) {
            log.info("Overdue detection job: no overdue invoices found for {}", today);
            return;
        }

        log.info("Overdue detection job: marking {} invoices as OVERDUE for {}",
                overdueInvoices.size(), today);

        for (Invoice invoice : overdueInvoices) {
            invoice.setStatus(InvoiceStatus.OVERDUE);
            log.info("Marked invoice {} ({}) as OVERDUE",
                    invoice.getInvoiceNumber(), invoice.getId());
        }
    }
}