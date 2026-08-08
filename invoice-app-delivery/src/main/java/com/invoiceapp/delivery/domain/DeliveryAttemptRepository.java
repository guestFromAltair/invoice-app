package com.invoiceapp.delivery.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface DeliveryAttemptRepository extends JpaRepository<DeliveryAttempt, UUID> {

    boolean existsByInvoiceId(UUID invoiceId);

    @Query(value = """
            SELECT * FROM delivery_attempts
            WHERE status = 'FAILED'
              AND next_attempt_at IS NOT NULL
              AND next_attempt_at < now()
            ORDER BY next_attempt_at
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<DeliveryAttempt> findDueForRetry(@Param("limit") int limit);
}