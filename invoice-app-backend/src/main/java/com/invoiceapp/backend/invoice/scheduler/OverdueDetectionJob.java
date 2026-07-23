package com.invoiceapp.backend.invoice.scheduler;

import com.invoiceapp.backend.invoice.service.InvoiceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class OverdueDetectionJob {

    private final InvoiceService invoiceService;

    @Scheduled(cron = "0 * * * * *")
    @SchedulerLock(name = "overdueDetection", lockAtLeastFor = "PT1M", lockAtMostFor = "PT10M")
    public void detectOverdueInvoices() {
        int count = invoiceService.markOverdueInvoices();
        log.info("Overdue detection: marked {} invoice(s) as OVERDUE", count);
    }
}