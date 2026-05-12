package com.invoiceapp.backend.shared.idempotency;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Component
@RequiredArgsConstructor
@Slf4j
public class IdempotencyCleanupJob {
    private final IdempotencyKeyRepository repository;

    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void cleanupExpiredKeys() {
        repository.deleteExpiredKeys(Instant.now());
        log.info("Idempotency key cleanup completed");
    }
}