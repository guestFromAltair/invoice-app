package com.invoiceapp.backend.shared.audit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AuditLog Entity Lifecycle")
class AuditLogTest {

    @Test
    @DisplayName("prePersist should automatically populate performedAt timestamp if null")
    void prePersist_populates_null_timestamp() {
        AuditLog auditLog = AuditLog.builder()
                .entityType("INVOICE")
                .action("CREATED")
                .build();

        assertThat(auditLog.getPerformedAt()).isNull();

        auditLog.prePersist();

        assertThat(auditLog.getPerformedAt()).isNotNull();

        assertThat(auditLog.getPerformedAt()).isBeforeOrEqualTo(Instant.now());
        assertThat(auditLog.getPerformedAt()).isAfter(Instant.now().minus(2, ChronoUnit.SECONDS));
    }

    @Test
    @DisplayName("prePersist should not overwrite performedAt timestamp if already explicitly provided")
    void prePersist_preserves_existing_timestamp() {
        Instant historicalTime = Instant.now().minus(5, ChronoUnit.DAYS);
        AuditLog auditLog = AuditLog.builder()
                .entityType("INVOICE")
                .action("CREATED")
                .performedAt(historicalTime)
                .build();

        auditLog.prePersist();

        assertThat(auditLog.getPerformedAt()).isEqualTo(historicalTime);
    }
}