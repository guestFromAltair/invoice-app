package com.invoiceapp.backend.invoice.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.QueryHint;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

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

    @Query(value = """
            SELECT
                (SELECT COALESCE(SUM(total), 0) FROM invoices
                 WHERE status IN ('SENT', 'OVERDUE'))
                -
                (SELECT COALESCE(SUM(p.amount), 0) FROM payments p
                 JOIN invoices i ON p.invoice_id = i.id
                 WHERE i.status IN ('SENT', 'OVERDUE'))
            """, nativeQuery = true)
    BigDecimal computeOutstandingBalance();

    /**
     * Streams all active invoices over a raw database cursor.
     * Prevents high-volume JVM heap memory inflation.
     */
    @Query("""
            SELECT i FROM Invoice i
            WHERE i.status IN (InvoiceStatus.SENT,InvoiceStatus.PAID,InvoiceStatus.OVERDUE)
            """)
    @QueryHints(value = {
            @QueryHint(name = "org.hibernate.fetchSize", value = "500"),
            @QueryHint(name = "jakarta.persistence.query.timeout", value = "60000")
    })
    Stream<Invoice> streamAllActiveInvoicesForReconciliation();

    /**
     * Streams stale draft invoices
     */
    @Query("""
            SELECT i FROM Invoice i
            WHERE i.status = InvoiceStatus.DRAFT
            AND i.issueDate < :cutoffDate
            """)
    @QueryHints(value = {
            @QueryHint(name = "org.hibernate.fetchSize", value = "500"),
            @QueryHint(name = "jakarta.persistence.query.timeout", value = "60000")
    })
    Stream<Invoice> streamStaleDraftsForReconciliation(@Param("cutoffDate") LocalDate cutoffDate);
}