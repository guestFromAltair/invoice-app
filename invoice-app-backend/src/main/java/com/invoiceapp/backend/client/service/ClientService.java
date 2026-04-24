package com.invoiceapp.backend.client.service;

import com.invoiceapp.backend.auth.domain.User;
import com.invoiceapp.backend.auth.domain.UserRepository;
import com.invoiceapp.backend.client.domain.Client;
import com.invoiceapp.backend.client.domain.ClientRepository;
import com.invoiceapp.backend.shared.exception.InvoiceAppException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ClientService {

    private final ClientRepository clientRepository;
    private final UserRepository userRepository;

    public record ClientRequest(
            String name,
            String email,
            String phone,
            String address,
            String vatNumber
    ) {}

    public record ClientResponse(
            UUID id,
            String name,
            String email,
            String phone,
            String address,
            String vatNumber,
            String createdAt
    ) {}

    private User getCurrentUser() {
        String email = Objects.requireNonNull(SecurityContextHolder.getContext()
                        .getAuthentication())
                .getName();

        return userRepository.findByEmail(email)
                .orElseThrow(() -> new InvoiceAppException(
                        "Authenticated user not found", HttpStatus.INTERNAL_SERVER_ERROR
                ));
    }

    @Transactional
    // Override the class-level readOnly = true for write operations.
    public ClientResponse create(ClientRequest request) {
        User owner = getCurrentUser();
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
        return toResponse(saved);
    }

    public Page<ClientResponse> findAll(Pageable pageable) {
        User owner = getCurrentUser();
        return clientRepository
                .findAllByOwnerId(owner.getId(), pageable)
                .map(this::toResponse);
    }

    public ClientResponse findById(UUID id) {
        User owner = getCurrentUser();
        Client client = clientRepository
                .findByIdAndOwnerId(id, owner.getId())
                .orElseThrow(() -> new InvoiceAppException(
                        "Client not found", HttpStatus.NOT_FOUND
                ));
        return toResponse(client);
    }

    @Transactional
    public ClientResponse update(UUID id, ClientRequest request) {
        User owner = getCurrentUser();
        Client client = clientRepository
                .findByIdAndOwnerId(id, owner.getId())
                .orElseThrow(() -> new InvoiceAppException(
                        "Client not found", HttpStatus.NOT_FOUND
                ));

        client.setName(request.name());
        client.setEmail(request.email());
        client.setPhone(request.phone());
        client.setAddress(request.address());
        client.setVatNumber(request.vatNumber());
        return toResponse(client);
    }

    @Transactional
    public void delete(UUID id) {
        User owner = getCurrentUser();
        Client client = clientRepository
                .findByIdAndOwnerId(id, owner.getId())
                .orElseThrow(() -> new InvoiceAppException(
                        "Client not found", HttpStatus.NOT_FOUND
                ));
        clientRepository.delete(client);
    }

    private ClientResponse toResponse(Client client) {
        return new ClientResponse(
                client.getId(),
                client.getName(),
                client.getEmail(),
                client.getPhone(),
                client.getAddress(),
                client.getVatNumber(),
                client.getCreatedAt().toString()
        );
    }
}