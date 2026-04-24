package com.invoiceapp.backend.client.controller;

import com.invoiceapp.backend.client.service.ClientService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/clients")
@RequiredArgsConstructor
public class ClientController {

    private final ClientService clientService;

    @GetMapping
    public Page<ClientService.ClientResponse> findAll(
            @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC)
            Pageable pageable
    ) {
        return clientService.findAll(pageable);
    }

    @GetMapping("/{id}")
    public ClientService.ClientResponse findById(@PathVariable UUID id) {
        return clientService.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ClientService.ClientResponse create(@Valid @RequestBody ClientRequest request) {
        return clientService.create(
                new ClientService.ClientRequest(
                        request.name(),
                        request.email(),
                        request.phone(),
                        request.address(),
                        request.vatNumber()
                )
        );
    }

    @PutMapping("/{id}")
    public ClientService.ClientResponse update(
            @PathVariable UUID id,
            @Valid @RequestBody ClientRequest request
    ) {
        return clientService.update(
                id,
                new ClientService.ClientRequest(
                        request.name(),
                        request.email(),
                        request.phone(),
                        request.address(),
                        request.vatNumber()
                )
        );
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        clientService.delete(id);
    }

    public record ClientRequest(
            @NotBlank(message = "Client name is required")
            @Size(max = 255, message = "Name must be under 255 characters")
            String name,
            String email,
            String phone,
            String address,
            String vatNumber
    ) {}
}