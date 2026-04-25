package com.invoiceapp.backend.invoice.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {

    Page<Invoice> findAllByCreatedById(UUID userId, Pageable pageable);

    Optional<Invoice> findByIdAndCreatedById(UUID id, UUID userId);

    @Query("""
        SELECT i FROM Invoice i
        WHERE i.status = 'SENT'
        AND i.dueDate < :today
        """)
    List<Invoice> findAllOverdue(@Param("today") LocalDate today);

    @Query("""
        SELECT i FROM Invoice i
        WHERE i.createdBy.id = :userId
        AND (:status IS NULL OR i.status = :status)
        AND (:clientId IS NULL OR i.client.id = :clientId)
        """)
    Page<Invoice> findAllByFilters(
            @Param("userId") UUID userId,
            @Param("status") InvoiceStatus status,
            @Param("clientId") UUID clientId,
            Pageable pageable
    );

    @Query("SELECT NEXTVAL('invoice_number_seq')")
    Long nextInvoiceSequence();
}