package com.invoiceapp.backend.client.service;

import com.invoiceapp.backend.auth.domain.User;
import com.invoiceapp.backend.client.domain.Client;
import com.invoiceapp.backend.client.domain.ClientRepository;
import com.invoiceapp.backend.shared.audit.AuditAction;
import com.invoiceapp.backend.shared.audit.AuditService;
import com.invoiceapp.backend.shared.exception.InvoiceAppException;
import com.invoiceapp.backend.shared.security.CurrentUserResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ClientService {

    private final ClientRepository clientRepository;
    private final CurrentUserResolver currentUserResolver;
    private final AuditService auditService;

    private static final String CLIENT = "CLIENT";

    public record ClientRequest(
            String name,
            String email,
            String phone,
            String address,
            String vatNumber,
            Long version
    ) {
    }

    public record ClientResponse(
            UUID id,
            String name,
            String email,
            String phone,
            String address,
            String vatNumber,
            Instant createdAt,
            Long version
    ) {
    }

    @Transactional
    // Override the class-level readOnly = true for write operations.
    public ClientResponse create(ClientRequest request) {
        User owner = currentUserResolver.resolveUser();
        if (clientRepository.existsByEmailAndOwnerId(request.email(), owner.getId())) {
            throw new InvoiceAppException(
                    "A client with this email already exists", HttpStatus.CONFLICT
            );
        }

        Client client = Client.builder()
                .owner(owner)
                .name(request.name())
                .email(request.email())
                .phone(request.phone())
                .address(request.address())
                .vatNumber(request.vatNumber())
                .build();

        Client saved = clientRepository.save(client);

        Map<String, String> newState = snapshotClientState(saved);
        auditService.log(
                CLIENT,
                saved.getId(),
                AuditAction.CLIENT_CREATED,
                null,
                newState,
                owner.getId()
        );

        return toResponse(saved);
    }

    public Page<ClientResponse> findAll(Pageable pageable) {
        User owner = currentUserResolver.resolveUser();
        return clientRepository
                .findAllByOwnerId(owner.getId(), pageable)
                .map(this::toResponse);
    }

    public ClientResponse findById(UUID id) {
        User owner = currentUserResolver.resolveUser();
        Client client = clientRepository
                .findByIdAndOwnerId(id, owner.getId())
                .orElseThrow(() -> new InvoiceAppException(
                        "Client not found", HttpStatus.NOT_FOUND
                ));
        return toResponse(client);
    }

    @Transactional
    public ClientResponse update(UUID id, ClientRequest request) {
        User owner = currentUserResolver.resolveUser();
        Client client = clientRepository
                .findByIdAndOwnerId(id, owner.getId())
                .orElseThrow(() -> new InvoiceAppException(
                        "Client not found", HttpStatus.NOT_FOUND
                ));

        if (request.version() != null && !client.getVersion().equals(request.version())) {
            throw new ObjectOptimisticLockingFailureException(Client.class, id);
        }

        Map<String, String> oldState = snapshotClientState(client);

        client.setName(request.name());
        client.setEmail(request.email());
        client.setPhone(request.phone());
        client.setAddress(request.address());
        client.setVatNumber(request.vatNumber());

        Map<String, String> newState = snapshotClientState(client);
        auditService.log(
                CLIENT,
                client.getId(),
                AuditAction.CLIENT_UPDATED,
                oldState,
                newState,
                owner.getId()
        );

        return toResponse(client);
    }

    @Transactional
    public void delete(UUID id) {
        User owner = currentUserResolver.resolveUser();
        Client client = clientRepository
                .findByIdAndOwnerId(id, owner.getId())
                .orElseThrow(() -> new InvoiceAppException(
                        "Client not found", HttpStatus.NOT_FOUND
                ));

        Map<String, String> oldState = snapshotClientState(client);
        auditService.log(
                CLIENT,
                client.getId(),
                AuditAction.CLIENT_DELETED,
                oldState,
                null,
                owner.getId()
        );

        clientRepository.delete(client);
    }

    private Map<String, String> snapshotClientState(Client client) {
        return Map.of(
                "owner", client.getOwner().getEmail(),
                "name", client.getName(),
                "createdAt", client.getCreatedAt().toString()
        );
    }

    private ClientResponse toResponse(Client client) {
        return new ClientResponse(
                client.getId(),
                client.getName(),
                client.getEmail(),
                client.getPhone(),
                client.getAddress(),
                client.getVatNumber(),
                client.getCreatedAt(),
                client.getVersion()
        );
    }
}