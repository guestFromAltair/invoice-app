package com.invoiceapp.backend.shared.idempotency;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@RequiredArgsConstructor
@Slf4j
public class IdempotencyCleanupJob {

    private final IdempotencyKeyRepository repository;

    @Value("${application.idempotency.abandoned-after-seconds}")
    private int abandonedAfterSeconds;

    @Scheduled(cron = "0 45 3 * * *")
    @SchedulerLock(name = "idempotencyCleanup", lockAtLeastFor = "PT1M", lockAtMostFor = "PT10M")
    public void deleteOldKeys() {
        Instant now = Instant.now();
        int deleted = repository.deleteExpiredOrStaleKeys(now, now.minusSeconds(abandonedAfterSeconds));
        log.info("Idempotency cleanup: removed {} expired or abandoned key(s)", deleted);
    }
}