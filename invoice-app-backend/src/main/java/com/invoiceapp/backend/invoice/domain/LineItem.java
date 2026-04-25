package com.invoiceapp.backend.invoice.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "line_items")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LineItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_id", nullable = false)
    private Invoice invoice;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal quantity;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal unitPrice;

    @Column(nullable = false, precision = 5, scale = 4)
    @Builder.Default
    private BigDecimal discountPct = BigDecimal.ZERO;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal lineTotal;

    @Column(nullable = false)
    @Builder.Default
    private Integer position = 0;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    public BigDecimal getLineTotal() {
        if (this.lineTotal == null) {
            computeLineTotal();
        }
        return this.lineTotal;
    }

    @PrePersist
    public void prePersist() {
        this.createdAt = Instant.now();
        computeLineTotal();
    }

    @PreUpdate
    public void preUpdate() {
        computeLineTotal();
    }

    public void computeLineTotal() {
        BigDecimal discount = BigDecimal.ONE.subtract(discountPct);
        this.lineTotal = quantity
                .multiply(unitPrice)
                .multiply(discount)
                .setScale(4, RoundingMode.HALF_UP);
    }
}