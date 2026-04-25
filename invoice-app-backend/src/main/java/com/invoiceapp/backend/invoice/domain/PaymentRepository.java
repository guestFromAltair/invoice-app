package com.invoiceapp.backend.invoice.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    List<Payment> findAllByInvoiceId(UUID invoiceId);

    @Query("""
        SELECT COALESCE(SUM(p.amount), 0)
        FROM Payment p
        WHERE p.invoice.id = :invoiceId
        """)
    BigDecimal sumAmountByInvoiceId(@Param("invoiceId") UUID invoiceId);
}