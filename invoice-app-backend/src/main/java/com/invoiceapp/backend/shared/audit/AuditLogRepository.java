package com.invoiceapp.backend.shared.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    List<AuditLog> findAllByEntityTypeAndEntityIdOrderByPerformedAtAsc(String entityType, UUID entityId);

    Page<AuditLog> findAllByPerformedByOrderByPerformedAtDesc(UUID performedBy, Pageable pageable);
}