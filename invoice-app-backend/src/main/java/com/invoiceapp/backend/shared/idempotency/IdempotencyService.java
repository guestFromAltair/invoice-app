package com.invoiceapp.backend.shared.idempotency;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
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
    private final ObjectMapper objectMapper;

    public Optional<StoredResponse> findExistingResponse(String idempotencyKey, UUID userId, String requestPath) {
        return repository
                .findByIdempotencyKeyAndUserIdAndRequestPath(idempotencyKey, userId, requestPath)
                .filter(key -> !key.isExpired())
                .map(key -> {
                    try {
                        return new StoredResponse(
                                key.getResponseStatus(),
                                objectMapper.readTree(key.getResponseBody())
                        );
                    } catch (Exception e) {
                        log.error("Failed to deserialize stored idempotency response", e);
                        return null;
                    }
                });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void storeResponse(
            String idempotencyKey,
            UUID userId,
            String requestPath,
            int responseStatus,
            Object responseBody
    ) {
        try {
            String serializedBody = objectMapper.writeValueAsString(responseBody);
            IdempotencyKey record = IdempotencyKey.builder()
                    .idempotencyKey(idempotencyKey)
                    .userId(userId)
                    .requestPath(requestPath)
                    .responseStatus(responseStatus)
                    .responseBody(serializedBody)
                    .build();

            repository.save(record);
        } catch (DataIntegrityViolationException e) {
            log.warn("Concurrent idempotency key insertion detected for key: {}", idempotencyKey);
        } catch (Exception e) {
            log.error("Failed to store idempotency key", e);
        }
    }

    public record StoredResponse(int status, Object body) {}
}