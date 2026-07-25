package com.invoiceapp.backend.shared.idempotency;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import com.invoiceapp.backend.shared.metrics.InvoiceMetrics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
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
    private JsonMapper jsonMapper;

    @Mock
    private InvoiceMetrics invoiceMetrics;

    @InjectMocks
    private IdempotencyService idempotencyService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(idempotencyService, "ttlSeconds", 86400);
        ReflectionTestUtils.setField(idempotencyService, "abandonedAfterSeconds", 300);
    }

    @Test
    @DisplayName("should return empty when no stored response exists")
    void findExistingResponse_Empty() {
        when(repository.findByIdempotencyKeyAndUserIdAndRequestPath(any(), any(), any())).thenReturn(Optional.empty());

        var result = idempotencyService.findExistingResponse("key", UUID.randomUUID(), "/path");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("should return status with null body when record is in ACCEPTED (processing) state")
    void findExistingResponse_Processing() {
        IdempotencyKey key = IdempotencyKey.builder()
                .responseStatus(202)
                .expiresAt(Instant.now().plusSeconds(100))
                .build();

        when(repository.findByIdempotencyKeyAndUserIdAndRequestPath(any(), any(), any())).thenReturn(Optional.of(key));

        var result = idempotencyService.findExistingResponse("key", UUID.randomUUID(), "/path");

        assertThat(result).isPresent();
        assertThat(result.get().status()).isEqualTo(202);
        assertThat(result.get().body()).isNull();
        verifyNoInteractions(jsonMapper);
    }

    @Test
    @DisplayName("should return parsed body when record is completed and not expired")
    void findExistingResponse_Completed() throws Exception {
        String rawJson = "{\"data\":\"ok\"}";
        IdempotencyKey key = IdempotencyKey.builder()
                .responseStatus(200)
                .responseBody(rawJson)
                .expiresAt(Instant.now().plusSeconds(100))
                .build();

        when(repository.findByIdempotencyKeyAndUserIdAndRequestPath(any(), any(), any()))
                .thenReturn(Optional.of(key));

        JsonNode realNode = JsonMapper.builder().build().readTree(rawJson);

        when(jsonMapper.readTree(rawJson)).thenReturn(realNode);

        var result = idempotencyService.findExistingResponse("key", UUID.randomUUID(), "/path");

        assertThat(result).isPresent();
        assertThat(result.get().status()).isEqualTo(200);
        assertThat(result.get().body().toString()).contains("ok");
    }

    @Test
    @DisplayName("tryLock returns true when the row is inserted or reclaimed")
    void tryLock_acquired() {
        when(repository.acquireLock(any(), any(), any(), any(), anyInt(), anyInt())).thenReturn(1);

        assertThat(idempotencyService.tryLock("key", UUID.randomUUID(), "/path")).isTrue();
    }

    @Test
    @DisplayName("tryLock returns false when another request holds the key")
    void tryLock_held() {
        when(repository.acquireLock(any(), any(), any(), any(), anyInt(), anyInt())).thenReturn(0);

        assertThat(idempotencyService.tryLock("key", UUID.randomUUID(), "/path")).isFalse();
    }

    @Test
    @DisplayName("tryLock returns false on unexpected failure")
    void tryLock_error() {
        when(repository.acquireLock(any(), any(), any(), any(), anyInt(), anyInt()))
                .thenThrow(new RuntimeException("boom"));

        assertThat(idempotencyService.tryLock("key", UUID.randomUUID(), "/path")).isFalse();
    }

    @Test
    @DisplayName("an abandoned PENDING record is treated as absent so it can be reclaimed")
    void abandonedPendingIsIgnored() {
        IdempotencyKey stale = IdempotencyKey.builder()
                .responseStatus(202)
                .responseBody("{\"status\":\"PENDING\"}")
                .createdAt(Instant.now().minusSeconds(600))
                .expiresAt(Instant.now().plusSeconds(86400))
                .build();
        when(repository.findByIdempotencyKeyAndUserIdAndRequestPath(any(), any(), any()))
                .thenReturn(Optional.of(stale));

        assertThat(idempotencyService.findExistingResponse("key", UUID.randomUUID(), "/path"))
                .isEmpty();
    }

    @Test
    @DisplayName("a recent PENDING record still blocks the request")
    void freshPendingStillBlocks() {
        IdempotencyKey pending = IdempotencyKey.builder()
                .responseStatus(202)
                .responseBody("{\"status\":\"PENDING\"}")
                .createdAt(Instant.now().minusSeconds(5))
                .expiresAt(Instant.now().plusSeconds(86400))
                .build();
        when(repository.findByIdempotencyKeyAndUserIdAndRequestPath(any(), any(), any()))
                .thenReturn(Optional.of(pending));

        var result = idempotencyService.findExistingResponse("key", UUID.randomUUID(), "/path");

        assertThat(result).isPresent();
        assertThat(result.get().status()).isEqualTo(202);
    }

    @Test
    @DisplayName("should update existing record with final status and body")
    void commitResponse_UpdateExisting() throws Exception {
        IdempotencyKey existing = spy(IdempotencyKey.class);
        UUID userId = UUID.randomUUID();

        when(repository.findByIdempotencyKeyAndUserIdAndRequestPath("key", userId, "/path"))
                .thenReturn(Optional.of(existing));
        when(jsonMapper.writeValueAsString(any())).thenReturn("{\"res\":\"data\"}");

        idempotencyService.commitResponse("key", userId, "/path", 200, "payload");

        verify(existing).setResponseStatus(200);
        verify(existing).setResponseBody("{\"res\":\"data\"}");
        verify(repository).save(existing);
    }

    @Test
    @DisplayName("should create new record if commit is called but no lock record existed")
    void commitResponse_CreateNewIfMissing() throws Exception {
        UUID userId = UUID.randomUUID();
        when(repository.findByIdempotencyKeyAndUserIdAndRequestPath(any(), any(), any())).thenReturn(Optional.empty());
        when(jsonMapper.writeValueAsString(any())).thenReturn("{}");

        idempotencyService.commitResponse("key", userId, "/path", 500, "error");

        verify(repository).save(argThat(key ->
                key.getIdempotencyKey().equals("key") && key.getResponseStatus() == 500
        ));
    }

    @Test
    @DisplayName("should not crash if JSON serialization fails during commit")
    void commitResponse_SerializationFailure() throws Exception {
        when(jsonMapper.writeValueAsString(any())).thenThrow(RuntimeException.class);

        idempotencyService.commitResponse("key", UUID.randomUUID(), "/path", 200, new Object());

        verify(repository, never()).save(any());
    }
}