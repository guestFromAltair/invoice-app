package com.invoiceapp.backend.shared.idempotency;

import tools.jackson.databind.json.JsonMapper;
import com.invoiceapp.backend.shared.metrics.InvoiceMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class IdempotencyService {

    private final IdempotencyKeyRepository repository;
    private final JsonMapper jsonMapper;
    private final InvoiceMetrics invoiceMetrics;

    public Optional<StoredResponse> findExistingResponse(String idempotencyKey, UUID userId, String requestPath) {
        return repository
                .findByIdempotencyKeyAndUserIdAndRequestPath(idempotencyKey, userId, requestPath)
                .filter(key -> !key.isExpired())
                .map(key -> {
                    try {
                        if (key.getResponseStatus() == HttpStatus.ACCEPTED.value()) {
                            return new StoredResponse(key.getResponseStatus(), null);
                        }
                        return new StoredResponse(
                                key.getResponseStatus(),
                                jsonMapper.readTree(key.getResponseBody())
                        );
                    } catch (Exception e) {
                        log.error("Failed to deserialize stored idempotency response", e);
                        return null;
                    }
                });
    }

    /**
     * PHASE 1: Acquire execution lock immediately.
     * Uses REQUIRES_NEW to instantly commit the lock row to PostgreSQL, blocking concurrent clicks.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean tryLock(String idempotencyKey, UUID userId, String requestPath) {
        try {
            IdempotencyKey record = IdempotencyKey.builder()
                    .idempotencyKey(idempotencyKey)
                    .userId(userId)
                    .requestPath(requestPath)
                    .responseStatus(HttpStatus.ACCEPTED.value())
                    .responseBody("{\"status\":\"PENDING\"}")
                    .build();

            // Force instant write to trigger DB constraint violations in case of concurrent requests.
            repository.saveAndFlush(record);
            return true;
        } catch (DataIntegrityViolationException e) {
            log.warn("Concurrent payment attempt blocked by DB constraint. Key: {}", idempotencyKey);
            return false;
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