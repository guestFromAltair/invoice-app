package com.invoiceapp.backend.client.service;

import com.invoiceapp.backend.auth.domain.Role;
import com.invoiceapp.backend.auth.domain.User;
import com.invoiceapp.backend.client.domain.Client;
import com.invoiceapp.backend.client.domain.ClientRepository;
import com.invoiceapp.backend.shared.audit.AuditAction;
import com.invoiceapp.backend.shared.audit.AuditService;
import com.invoiceapp.backend.shared.exception.InvoiceAppException;
import com.invoiceapp.backend.shared.security.CurrentUserResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ClientService")
class ClientServiceTest {

    @Mock
    private ClientRepository clientRepository;
    @Mock
    private CurrentUserResolver currentUserResolver;
    @Mock
    private AuditService auditService;

    @InjectMocks
    private ClientService clientService;

    private User testUser;
    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        testUser = User.builder()
                .id(userId)
                .email("test@example.com")
                .password("hashed")
                .role(Role.USER)
                .build();

        lenient().when(currentUserResolver.resolveUser()).thenReturn(testUser);
    }

    @Nested
    @DisplayName("create client")
    class CreateClient {

        @Test
        @DisplayName("should create a client successfully and log audit creation event")
        void should_create_client_successfully() {
            UUID clientId = UUID.randomUUID();
            Instant now = Instant.now();

            when(clientRepository.existsByEmailAndOwnerId("client@acme.com", userId)).thenReturn(false);
            when(clientRepository.save(any())).thenAnswer(inv -> {
                Client c = inv.getArgument(0);
                c.setId(clientId);
                c.setCreatedAt(now);
                return c;
            });

            ClientService.ClientResponse response = clientService.create(
                    new ClientService.ClientRequest(
                            "Acme Corp",
                            "client@acme.com",
                            "+33123456789",
                            "12 Rue de Rivoli, Paris",
                            "FR12345678901",
                            null
                    )
            );

            assertThat(response.name()).isEqualTo("Acme Corp");
            assertThat(response.email()).isEqualTo("client@acme.com");

            verify(clientRepository).save(argThat(client -> testUser.equals(client.getOwner())));

            verify(auditService, times(1)).log(
                    eq("CLIENT"),
                    eq(clientId),
                    eq(AuditAction.CLIENT_CREATED),
                    isNull(),
                    eq(Map.of("owner", "test@example.com", "name", "Acme Corp", "createdAt", now.toString())),
                    eq(userId)
            );
        }

        @Test
        @DisplayName("should throw 409 when client email already exists and skip audit trail logs")
        void should_throw_conflict_for_duplicate_email() {
            when(clientRepository.existsByEmailAndOwnerId("duplicate@acme.com", userId)).thenReturn(true);

            assertThatThrownBy(() -> clientService.create(
                    new ClientService.ClientRequest(
                            "Acme Corp",
                            "duplicate@acme.com",
                            null, null, null, 1L
                    )
            ))
                    .isInstanceOf(InvoiceAppException.class)
                    .hasMessageContaining("already exists");

            verify(clientRepository, never()).save(any());
            verifyNoInteractions(auditService);
        }
    }

    @Nested
    @DisplayName("find clients")
    class FindClients {

        @Test
        @DisplayName("should return only clients belonging to the current user and bypass audit service entirely")
        void should_return_only_current_user_clients() {
            Client client = Client.builder()
                    .id(UUID.randomUUID())
                    .owner(testUser)
                    .name("My Client")
                    .createdAt(Instant.now())
                    .build();

            var pageable = PageRequest.of(0, 20);
            when(clientRepository.findAllByOwnerId(userId, pageable)).thenReturn(new PageImpl<>(List.of(client)));

            var result = clientService.findAll(pageable);

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().getFirst().name()).isEqualTo("My Client");

            verify(clientRepository).findAllByOwnerId(userId, pageable);
            verifyNoInteractions(auditService);
        }

        @Test
        @DisplayName("should throw 404 when client not found or belongs to another user")
        void should_throw_404_when_client_not_found() {
            UUID randomId = UUID.randomUUID();
            when(clientRepository.findByIdAndOwnerId(randomId, userId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> clientService.findById(randomId))
                    .isInstanceOf(InvoiceAppException.class)
                    .hasMessageContaining("not found");

            verifyNoInteractions(auditService);
        }
    }

    @Nested
    @DisplayName("update client")
    class UpdateClient {

        @Test
        @DisplayName("should update client fields correctly and track old vs new state transformations")
        void should_update_client_fields() {
            UUID clientId = UUID.randomUUID();
            Instant now = Instant.now();

            Client existing = Client.builder()
                    .id(clientId)
                    .owner(testUser)
                    .name("Old Name")
                    .email("old@acme.com")
                    .createdAt(now)
                    .version(1L)
                    .build();

            when(clientRepository.findByIdAndOwnerId(clientId, userId)).thenReturn(Optional.of(existing));

            ClientService.ClientResponse response = clientService.update(
                    clientId,
                    new ClientService.ClientRequest(
                            "New Name",
                            "new@acme.com",
                            null, null, null, 1L
                    )
            );

            assertThat(response.name()).isEqualTo("New Name");
            assertThat(response.email()).isEqualTo("new@acme.com");

            verify(auditService, times(1)).log(
                    eq("CLIENT"),
                    eq(clientId),
                    eq(AuditAction.CLIENT_UPDATED),
                    eq(Map.of("owner", "test@example.com", "name", "Old Name", "createdAt", now.toString())),
                    eq(Map.of("owner", "test@example.com", "name", "New Name", "createdAt", now.toString())),
                    eq(userId)
            );
        }
    }

    @Nested
    @DisplayName("delete client")
    class DeleteClient {

        @Test
        @DisplayName("should delete client when found and register a terminal deletion audit row")
        void should_delete_client_when_found() {
            UUID clientId = UUID.randomUUID();
            Instant now = Instant.now();

            Client client = Client.builder()
                    .id(clientId)
                    .owner(testUser)
                    .name("To Delete")
                    .createdAt(now)
                    .build();

            when(clientRepository.findByIdAndOwnerId(clientId, userId)).thenReturn(Optional.of(client));

            clientService.delete(clientId);

            verify(clientRepository).delete(client);

            verify(auditService, times(1)).log(
                    eq("CLIENT"),
                    eq(clientId),
                    eq(AuditAction.CLIENT_DELETED),
                    eq(Map.of("owner", "test@example.com", "name", "To Delete", "createdAt", now.toString())),
                    isNull(),
                    eq(userId)
            );
        }

        @Test
        @DisplayName("should throw 404 and safely skip audit updates when deleting non-existent entry")
        void should_throw_404_when_deleting_non_existent_client() {
            UUID randomId = UUID.randomUUID();
            when(clientRepository.findByIdAndOwnerId(randomId, userId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> clientService.delete(randomId))
                    .isInstanceOf(InvoiceAppException.class)
                    .hasMessageContaining("not found");

            verify(clientRepository, never()).delete(any());
            verifyNoInteractions(auditService);
        }
    }

    @Test
    @DisplayName("should throw ObjectOptimisticLockingFailureException and skip audit modification logs on concurrent conflicts")
    void should_throw_optimistic_lock_exception_on_version_mismatch() {
        UUID clientId = UUID.randomUUID();
        Client existingClient = Client.builder()
                .id(clientId)
                .owner(testUser)
                .name("Old Name")
                .email("old@acme.com")
                .createdAt(Instant.now())
                .version(5L)
                .build();

        when(clientRepository.findByIdAndOwnerId(clientId, userId)).thenReturn(Optional.of(existingClient));

        assertThatThrownBy(() -> clientService.update(
                clientId,
                new ClientService.ClientRequest(
                        "New Name", "new@acme.com", null, null, null, 4L
                )
        )).isInstanceOf(ObjectOptimisticLockingFailureException.class);

        assertThat(existingClient.getName()).isEqualTo("Old Name");
        verify(auditService, never()).log(any(), any(), any(), any(), any(), any());
    }
}