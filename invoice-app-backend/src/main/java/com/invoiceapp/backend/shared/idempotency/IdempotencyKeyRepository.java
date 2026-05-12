package com.invoiceapp.backend.shared.idempotency;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, UUID> {
    Optional<IdempotencyKey> findByIdempotencyKeyAndUserIdAndRequestPath(
            String idempotencyKey,
            UUID userId,
            String requestPath
    );

    @Modifying
    @Query("DELETE FROM IdempotencyKey i WHERE i.expiresAt < :now")
    void deleteExpiredKeys(Instant now);
}