package com.invoiceapp.backend.shared.idempotency;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Component
@RequiredArgsConstructor
@Slf4j
public class IdempotencyCleanupJob {
    private final IdempotencyKeyRepository repository;

    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void cleanupKeys() {
        Instant now = Instant.now();
        Instant thirtyMinutesAgo = now.minus(30, ChronoUnit.MINUTES);

        repository.deleteExpiredOrStaleKeys(now, thirtyMinutesAgo);
        log.info("Cleanup completed: removed expired keys and stale locks.");
    }
}