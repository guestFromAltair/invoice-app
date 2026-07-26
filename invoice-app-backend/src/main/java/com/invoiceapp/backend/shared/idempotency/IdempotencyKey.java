package com.invoiceapp.backend.shared.idempotency;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "idempotency_keys")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IdempotencyKey {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String idempotencyKey;

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private String requestPath;

    @Column(nullable = false)
    private Integer responseStatus;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String responseBody;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant expiresAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = Instant.now();
        this.expiresAt = Instant.now().plusSeconds(86400);
    }

    public boolean isExpired() {
        return Instant.now().isAfter(this.expiresAt);
    }
}