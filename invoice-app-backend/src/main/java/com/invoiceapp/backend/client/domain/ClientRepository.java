package com.invoiceapp.backend.client.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ClientRepository extends JpaRepository<Client, UUID> {

    Page<Client> findAllByOwnerId(UUID ownerId, Pageable pageable);

    Optional<Client> findByIdAndOwnerId(UUID id, UUID ownerId);

    boolean existsByEmailAndOwnerId(String email, UUID ownerId);
}