package com.invoiceapp.backend.shared.outbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxCleanupJob {

    private final OutboxEventRepository outboxEventRepository;

    @Value("${application.outbox.retention-days}")
    private int retentionDays;

    @Scheduled(cron = "0 15 4 * * *")
    @SchedulerLock(name = "outboxCleanup", lockAtLeastFor = "PT1M", lockAtMostFor = "PT15M")
    public void deletePublishedEvents() {
        Instant cutoff = Instant.now().minus(Duration.ofDays(retentionDays));
        int deleted = outboxEventRepository.deletePublishedBefore(cutoff);
        log.info("Outbox cleanup: removed {} published event(s) older than {} day(s)", deleted, retentionDays);
    }
}