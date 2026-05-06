package com.invoiceapp.backend.client.service;

import com.invoiceapp.backend.auth.domain.Role;
import com.invoiceapp.backend.auth.domain.User;
import com.invoiceapp.backend.auth.domain.UserRepository;
import com.invoiceapp.backend.client.domain.Client;
import com.invoiceapp.backend.client.domain.ClientRepository;
import com.invoiceapp.backend.shared.exception.InvoiceAppException;
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
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
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
    private UserRepository userRepository;

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

        Authentication authentication = mock(Authentication.class);
        when(authentication.getName()).thenReturn("test@example.com");
        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);

        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
    }

    @Nested
    @DisplayName("create client")
    class CreateClient {

        @Test
        @DisplayName("should create a client successfully")
        void should_create_client_successfully() {
            when(clientRepository.existsByEmailAndOwnerId(
                    "client@acme.com", userId))
                    .thenReturn(false);
            when(clientRepository.save(any())).thenAnswer(inv -> {
                Client c = inv.getArgument(0);
                c.setId(UUID.randomUUID());
                return c;
            });

            ClientService.ClientResponse response = clientService.create(
                    new ClientService.ClientRequest(
                            "Acme Corp",
                            "client@acme.com",
                            "+33123456789",
                            "12 Rue de Rivoli, Paris",
                            "FR12345678901"
                    )
            );

            assertThat(response.name()).isEqualTo("Acme Corp");
            assertThat(response.email()).isEqualTo("client@acme.com");
            verify(clientRepository).save(argThat(client ->
                    testUser.equals(client.getOwner())
            ));
        }

        @Test
        @DisplayName("should throw 409 when client email already exists for this user")
        void should_throw_conflict_for_duplicate_email() {
            when(clientRepository.existsByEmailAndOwnerId(
                    "duplicate@acme.com", userId))
                    .thenReturn(true);

            assertThatThrownBy(() -> clientService.create(
                    new ClientService.ClientRequest(
                            "Acme Corp",
                            "duplicate@acme.com",
                            null, null, null
                    )
            ))
                    .isInstanceOf(InvoiceAppException.class)
                    .hasMessageContaining("already exists");

            verify(clientRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("find clients")
    class FindClients {

        @Test
        @DisplayName("should return only clients belonging to the current user")
        void should_return_only_current_user_clients() {
            Client client = Client.builder()
                    .id(UUID.randomUUID())
                    .owner(testUser)
                    .name("My Client")
                    .build();

            var pageable = PageRequest.of(0, 20);
            when(clientRepository.findAllByOwnerId(userId, pageable)).thenReturn(new PageImpl<>(List.of(client)));

            var result = clientService.findAll(pageable);

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().getFirst().name()).isEqualTo("My Client");

            verify(clientRepository).findAllByOwnerId(userId, pageable);
        }

        @Test
        @DisplayName("should throw 404 when client not found or belongs to another user")
        void should_throw_404_when_client_not_found() {
            UUID randomId = UUID.randomUUID();
            when(clientRepository.findByIdAndOwnerId(randomId, userId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> clientService.findById(randomId))
                    .isInstanceOf(InvoiceAppException.class)
                    .hasMessageContaining("not found");
        }
    }

    @Nested
    @DisplayName("update client")
    class UpdateClient {

        @Test
        @DisplayName("should update client fields correctly")
        void should_update_client_fields() {
            Client existing = Client.builder()
                    .id(UUID.randomUUID())
                    .owner(testUser)
                    .name("Old Name")
                    .email("old@acme.com")
                    .build();

            when(clientRepository.findByIdAndOwnerId(existing.getId(), userId)).thenReturn(Optional.of(existing));

            ClientService.ClientResponse response = clientService.update(
                    existing.getId(),
                    new ClientService.ClientRequest(
                            "New Name",
                            "new@acme.com",
                            null, null, null
                    )
            );

            assertThat(response.name()).isEqualTo("New Name");
            assertThat(response.email()).isEqualTo("new@acme.com");
        }
    }

    @Nested
    @DisplayName("delete client")
    class DeleteClient {

        @Test
        @DisplayName("should delete client when found")
        void should_delete_client_when_found() {
            Client client = Client.builder()
                    .id(UUID.randomUUID())
                    .owner(testUser)
                    .name("To Delete")
                    .build();

            when(clientRepository.findByIdAndOwnerId(client.getId(), userId)).thenReturn(Optional.of(client));

            clientService.delete(client.getId());

            verify(clientRepository).delete(client);
        }

        @Test
        @DisplayName("should throw 404 when deleting non-existent client")
        void should_throw_404_when_deleting_non_existent_client() {
            UUID randomId = UUID.randomUUID();
            when(clientRepository.findByIdAndOwnerId(randomId, userId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> clientService.delete(randomId))
                    .isInstanceOf(InvoiceAppException.class)
                    .hasMessageContaining("not found");

            verify(clientRepository, never()).delete(any());
        }
    }
}