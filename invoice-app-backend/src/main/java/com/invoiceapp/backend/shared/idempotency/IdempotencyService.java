package com.invoiceapp.backend.shared.idempotency;

import org.springframework.beans.factory.annotation.Value;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import com.invoiceapp.backend.shared.metrics.InvoiceMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class IdempotencyService {

    private final IdempotencyKeyRepository repository;
    private final JsonMapper jsonMapper;
    private final InvoiceMetrics invoiceMetrics;

    private static final String PENDING_BODY = "{\"status\":\"PENDING\"}";

    @Value("${application.idempotency.ttl-seconds}")
    private int ttlSeconds;

    @Value("${application.idempotency.abandoned-after-seconds}")
    private int abandonedAfterSeconds;

    public Optional<StoredResponse> findExistingResponse(String idempotencyKey, UUID userId, String requestPath) {
        return repository
                .findByIdempotencyKeyAndUserIdAndRequestPath(idempotencyKey, userId, requestPath)
                .filter(key -> !key.isExpired())
                .filter(key -> !isAbandonedPending(key))
                .map(key -> {
                    try {
                        if (key.getResponseStatus() == HttpStatus.ACCEPTED.value()) {
                            return new StoredResponse(key.getResponseStatus(), null);
                        }
                        JsonNode body = jsonMapper.readTree(key.getResponseBody());
                        return new StoredResponse(
                                key.getResponseStatus(),
                                (body == null || body.isNull()) ? null : body
                        );
                    } catch (Exception e) {
                        log.error("Failed to deserialize stored idempotency response", e);
                        return null;
                    }
                });
    }

    /**
     * A PENDING row left behind by a request that never finished. Treated as
     * absent so the caller falls through to tryLock and reclaims it.
     * This is only a hint — the upsert's WHERE clause is the real arbiter.
     */
    private boolean isAbandonedPending(IdempotencyKey key) {
        return key.getResponseStatus() == HttpStatus.ACCEPTED.value()
                && key.getCreatedAt() != null
                && key.getCreatedAt().isBefore(Instant.now().minusSeconds(abandonedAfterSeconds));
    }

    /**
     * PHASE 1: Acquire the execution lock.
     * A single upsert either inserts the row or takes over a dead one.
     * 0 rows changed means another request is genuinely holding the key.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean tryLock(String idempotencyKey, UUID userId, String requestPath) {
        try {
            int rows = repository.acquireLock(
                    idempotencyKey, userId, requestPath,
                    PENDING_BODY, ttlSeconds, abandonedAfterSeconds
            );

            if (rows == 0) {
                log.warn("Idempotency key is actively held. Key: {}", idempotencyKey);
                return false;
            }
            return true;
        } catch (Exception e) {
            log.error("Failed to acquire idempotency lock", e);
            return false;
        }
    }

    /**
     * PHASE 2: Overwrite original placeholder row with final results.
     * Uses REQUIRES_NEW to ensure the receipt payload is saved even if outer transactions roll back.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void commitResponse(
            String idempotencyKey,
            UUID userId,
            String requestPath,
            int responseStatus,
            Object responseBody
    ) {
        try {
            String serializedBody = jsonMapper.writeValueAsString(responseBody);
            repository.findByIdempotencyKeyAndUserIdAndRequestPath(idempotencyKey, userId, requestPath)
                    .ifPresentOrElse(record -> {
                        record.setResponseStatus(responseStatus);
                        record.setResponseBody(serializedBody);
                        repository.save(record);
                        log.info("Idempotency record committed successfully for key: {}", idempotencyKey);
                    }, () -> {
                        // Fallback edge-case safeguard
                        IdempotencyKey record = IdempotencyKey.builder()
                                .idempotencyKey(idempotencyKey)
                                .userId(userId)
                                .requestPath(requestPath)
                                .responseStatus(responseStatus)
                                .responseBody(serializedBody)
                                .build();
                        repository.save(record);
                    });
        } catch (Exception e) {
            log.error("CRITICAL: Failed to commit final response payload for idempotency key: {}", idempotencyKey, e);
            invoiceMetrics.recordIdempotencyCommitFailure();
        }
    }

    public record StoredResponse(int status, Object body) {
    }
}