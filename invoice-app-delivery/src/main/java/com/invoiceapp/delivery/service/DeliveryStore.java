package com.invoiceapp.delivery.service;

import com.invoiceapp.delivery.domain.DeliveryAttempt;
import com.invoiceapp.delivery.domain.DeliveryAttemptRepository;
import com.invoiceapp.delivery.domain.DeliveryStatus;
import com.invoiceapp.delivery.event.InvoiceReadyForDeliveryEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class DeliveryStore {

    private final DeliveryAttemptRepository repository;

    @Value("${application.delivery.retry-lease-seconds}")
    private long retryLeaseSeconds;

    @Transactional
    public Optional<DeliveryAttempt> claimNew(InvoiceReadyForDeliveryEvent event, String rawPayload) {
        if (repository.existsByInvoiceId(event.invoiceId())) {
            return Optional.empty();
        }

        DeliveryAttempt attempt = DeliveryAttempt.builder()
                .invoiceId(event.invoiceId())
                .invoiceNumber(event.invoiceNumber())
                .ownerId(event.ownerId())
                .recipient(event.recipient().email())
                .status(DeliveryStatus.PENDING)
                .attempts(0)
                .payload(rawPayload)
                .build();

        try {
            repository.saveAndFlush(attempt);
            return Optional.of(attempt);
        } catch (DataIntegrityViolationException race) {
            // Another consumer claimed it in the same instant.
            return Optional.empty();
        }
    }

    @Transactional
    public List<DeliveryAttempt> claimDueForRetry(int limit) {
        List<DeliveryAttempt> due = repository.findDueForRetry(limit);
        if (due.isEmpty()) {
            return List.of();
        }

        Instant lease = Instant.now().plusSeconds(retryLeaseSeconds);
        due.forEach(attempt -> attempt.setNextAttemptAt(lease));
        repository.saveAll(due);

        return due;
    }

    @Transactional
    public void recordOutcome(UUID attemptId, DeliveryOutcome outcome) {
        DeliveryAttempt attempt = repository.findById(attemptId).orElse(null);
        if (attempt == null) {
            log.error("Delivery attempt {} disappeared before its result could be recorded", attemptId);
            return;
        }

        attempt.setAttempts(outcome.attempts());
        attempt.setStatus(outcome.status());
        attempt.setLastError(outcome.error());
        attempt.setNextAttemptAt(outcome.nextAttemptAt());

        repository.save(attempt);
    }
}