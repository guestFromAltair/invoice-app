package com.invoiceapp.backend.shared.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

@Component
@RequiredArgsConstructor
@Slf4j
public class ProcessedEventCleanupJob {

    private final ProcessedEventRepository processedEventRepository;

    @Value("${application.kafka.consumer.processed-retention-days}")
    private int retentionDays;

    @Scheduled(cron = "0 30 3 * * *")
    public void deleteOldProcessedEvents() {
        Instant cutoff = Instant.now().minus(Duration.ofDays(retentionDays));
        int deleted = processedEventRepository.deleteProcessedBefore(cutoff);
        log.info("Processed-events cleanup: removed {} row(s) older than {} day(s)", deleted, retentionDays);
    }
}