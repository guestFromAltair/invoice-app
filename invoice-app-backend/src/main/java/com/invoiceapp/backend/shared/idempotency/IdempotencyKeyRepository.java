package com.invoiceapp.backend.shared.idempotency;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

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
    @Query(value = """
            INSERT INTO idempotency_keys
                (id, idempotency_key, user_id, request_path, response_status, response_body, created_at, expires_at)
            VALUES
                (gen_random_uuid(), :key, :userId, :path, 202, :pendingBody, now(), now() + (:ttlSeconds * INTERVAL '1 second'))
            ON CONFLICT ON CONSTRAINT uq_idempotency_key_user_path
            DO UPDATE SET
                response_status = 202,
                response_body   = :pendingBody,
                created_at      = now(),
                expires_at      = now() + (:ttlSeconds * INTERVAL '1 second')
            WHERE idempotency_keys.expires_at < now()
               OR (idempotency_keys.response_status = 202
                   AND idempotency_keys.created_at
                       < now() - (:abandonedSeconds * INTERVAL '1 second'))
            """, nativeQuery = true)
    int acquireLock(@Param("key") String key,
                    @Param("userId") UUID userId,
                    @Param("path") String path,
                    @Param("pendingBody") String pendingBody,
                    @Param("ttlSeconds") int ttlSeconds,
                    @Param("abandonedSeconds") int abandonedSeconds);

    @Modifying
    @Transactional
    @Query("DELETE FROM IdempotencyKey i WHERE i.expiresAt < :now " +
            "OR (i.responseStatus = 202 AND i.createdAt < :staleTime)")
    int deleteExpiredOrStaleKeys(@Param("now") Instant now, @Param("staleTime") Instant staleTime);
}