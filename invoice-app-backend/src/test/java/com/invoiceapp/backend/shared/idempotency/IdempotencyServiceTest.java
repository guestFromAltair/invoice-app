package com.invoiceapp.backend.shared.idempotency;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("IdempotencyService")
class IdempotencyServiceTest {

    @Mock
    private IdempotencyKeyRepository repository;
    @Mock
    private final ObjectMapper objectMapper = new ObjectMapper();
    @InjectMocks
    private IdempotencyService idempotencyService;

    @Test
    @DisplayName("should return empty when no stored response exists")
    void should_return_empty_when_no_stored_response() {
        when(repository.findByIdempotencyKeyAndUserIdAndRequestPath(
                        any(), any(), any()
                )
        ).thenReturn(Optional.empty());

        var result = idempotencyService.findExistingResponse(
                "key-123", UUID.randomUUID(), "/api/payments"
        );

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("should return stored response when key exists and is not expired")
    void should_return_stored_response_when_key_exists() throws Exception {
        IdempotencyKey storedKey = IdempotencyKey.builder()
                .idempotencyKey("key-123")
                .userId(UUID.randomUUID())
                .requestPath("/api/invoices/uuid/payments")
                .responseStatus(201)
                .responseBody("{\"id\":\"payment-uuid\",\"amount\":500.00}")
                .expiresAt(java.time.Instant.now().plusSeconds(3600))
                .build();

        when(repository.findByIdempotencyKeyAndUserIdAndRequestPath(
                any(), any(), any()))
                .thenReturn(Optional.of(storedKey));

        var result = idempotencyService.findExistingResponse(
                "key-123", UUID.randomUUID(), "/api/invoices/uuid/payments"
        );

        assertThat(result).isPresent();
        assertThat(result.get().status()).isEqualTo(201);
    }

    @Test
    @DisplayName("should return empty for expired keys")
    void should_return_empty_for_expired_keys() {
        IdempotencyKey expiredKey = IdempotencyKey.builder()
                .idempotencyKey("key-123")
                .userId(UUID.randomUUID())
                .requestPath("/api/invoices/uuid/payments")
                .responseStatus(201)
                .responseBody("{\"amount\":500.00}")
                .expiresAt(java.time.Instant.now().minusSeconds(3600))
                .build();

        when(repository.findByIdempotencyKeyAndUserIdAndRequestPath(
                any(), any(), any()))
                .thenReturn(Optional.of(expiredKey));

        var result = idempotencyService.findExistingResponse(
                "key-123", UUID.randomUUID(), "/api/invoices/uuid/payments"
        );

        assertThat(result).isEmpty();
    }
}