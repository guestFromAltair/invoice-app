package com.invoiceapp.backend.shared.audit;

import com.invoiceapp.backend.client.domain.Client;
import com.invoiceapp.backend.client.domain.ClientRepository;
import com.invoiceapp.backend.invoice.domain.Invoice;
import com.invoiceapp.backend.invoice.domain.InvoiceRepository;
import com.invoiceapp.backend.auth.domain.User;
import com.invoiceapp.backend.shared.exception.InvoiceAppException;
import com.invoiceapp.backend.shared.security.CurrentUserResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuditService Deep Structural Coverage Tests")
class AuditServiceTest {

    @Mock private AuditLogRepository auditLogRepository;
    @Mock private JsonMapper jsonMapper;
    @Mock private CurrentUserResolver currentUserResolver;
    @Mock private InvoiceRepository invoiceRepository;
    @Mock private ClientRepository clientRepository;

    @InjectMocks
    private AuditService auditService;

    @AfterEach
    void cleanUpRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Nested
    @DisplayName("History Retrieval Operational Logic")
    class HistoryRetrieval {

        @Test
        @DisplayName("should return invoice history when user owns the requested invoice")
        void should_get_invoice_history_when_authorized() {
            UUID invoiceId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            User user = User.builder().id(userId).build();
            Invoice invoice = Invoice.builder().id(invoiceId).build();
            AuditLog logEntry = AuditLog.builder().entityId(invoiceId).action("INVOICE_CREATED").build();

            when(currentUserResolver.resolveUser()).thenReturn(user);
            when(invoiceRepository.findByIdAndCreatedById(invoiceId, userId)).thenReturn(Optional.of(invoice));
            when(auditLogRepository.findAllByEntityTypeAndEntityIdOrderByPerformedAtAsc("INVOICE", invoiceId))
                    .thenReturn(List.of(logEntry));

            List<AuditLog> history = auditService.getInvoiceHistory(invoiceId);

            assertThat(history).hasSize(1);
            assertThat(history.getFirst().getAction()).isEqualTo("INVOICE_CREATED");
        }

        @Test
        @DisplayName("should throw 404 Exception when user tries to read an invoice history they do not own")
        void should_throw_404_when_invoice_not_found_or_unauthorized() {
            UUID invoiceId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            User user = User.builder().id(userId).build();

            when(currentUserResolver.resolveUser()).thenReturn(user);
            when(invoiceRepository.findByIdAndCreatedById(invoiceId, userId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> auditService.getInvoiceHistory(invoiceId))
                    .isInstanceOf(InvoiceAppException.class)
                    .hasMessage("Invoice not found");
        }

        @Test
        @DisplayName("should return client history when user owns the requested client record")
        void should_get_client_history_when_authorized() {
            UUID clientId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            User user = User.builder().id(userId).build();
            Client client = Client.builder().id(clientId).build();
            AuditLog logEntry = AuditLog.builder().entityId(clientId).action("CLIENT_CREATED").build();

            when(currentUserResolver.resolveUser()).thenReturn(user);
            when(clientRepository.findByIdAndOwnerId(clientId, userId)).thenReturn(Optional.of(client));
            when(auditLogRepository.findAllByEntityTypeAndEntityIdOrderByPerformedAtAsc("CLIENT", clientId))
                    .thenReturn(List.of(logEntry));

            List<AuditLog> history = auditService.getClientHistory(clientId);

            assertThat(history).hasSize(1);
            assertThat(history.getFirst().getAction()).isEqualTo("CLIENT_CREATED");
        }

        @Test
        @DisplayName("should throw 404 Exception when user tries to read a client history they do not own")
        void should_throw_404_when_client_not_found_or_unauthorized() {
            UUID clientId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            User user = User.builder().id(userId).build();

            when(currentUserResolver.resolveUser()).thenReturn(user);
            when(clientRepository.findByIdAndOwnerId(clientId, userId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> auditService.getClientHistory(clientId))
                    .isInstanceOf(InvoiceAppException.class)
                    .hasMessage("Client not found");
        }

        @Test
        @DisplayName("should return generic entity history directly without tenant security mapping checks")
        void should_get_generic_entity_history_directly() {
            UUID userId = UUID.randomUUID();
            AuditLog logEntry = AuditLog.builder().entityId(userId).action("USER_LOGIN").build();

            when(auditLogRepository.findAllByEntityTypeAndEntityIdOrderByPerformedAtAsc("USER", userId))
                    .thenReturn(List.of(logEntry));

            List<AuditLog> history = auditService.getEntityHistory("USER", userId);

            assertThat(history).hasSize(1);
            assertThat(history.getFirst().getAction()).isEqualTo("USER_LOGIN");
            verifyNoInteractions(invoiceRepository, clientRepository, currentUserResolver);
        }
    }

    @Nested
    @DisplayName("Deep Structural Logging Engine Operations")
    class AuditLoggingPipeline {

        @Test
        @DisplayName("should save an audit log record successfully even if values are null and no request context exists")
        void should_log_audit_action_successfully_with_minimal_data() {
            UUID entityId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();

            auditService.log("INVOICE", entityId, "INVOICE_UPDATED", null, null, userId);

            ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
            verify(auditLogRepository, times(1)).save(captor.capture());

            AuditLog savedEntry = captor.getValue();
            assertThat(savedEntry.getEntityType()).isEqualTo("INVOICE");
            assertThat(savedEntry.getAction()).isEqualTo("INVOICE_UPDATED");
            assertThat(savedEntry.getPerformedBy()).isEqualTo(userId);
            assertThat(savedEntry.getIpAddress()).isNull();
            assertThat(savedEntry.getRequestId()).isNull();
        }

        @Test
        @DisplayName("should successfully execute full structural JSON mapping string translations")
        void should_serialize_objects_to_json_strings_successfully() throws Exception {
            UUID entityId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            Object originalState = List.of("oldData");
            Object mutatedState = List.of("newData");

            when(jsonMapper.writeValueAsString(originalState)).thenReturn("[\"oldData\"]");
            when(jsonMapper.writeValueAsString(mutatedState)).thenReturn("[\"newData\"]");

            auditService.log("INVOICE", entityId, "INVOICE_UPDATED", originalState, mutatedState, userId);

            ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
            verify(auditLogRepository).save(captor.capture());
            assertThat(captor.getValue().getOldValue()).isEqualTo("[\"oldData\"]");
            assertThat(captor.getValue().getNewValue()).isEqualTo("[\"newData\"]");
        }

        @Test
        @DisplayName("should catch Jackson exceptions gracefully and write structural recovery fallbacks to DB")
        void should_fallback_gracefully_when_json_serialization_fails() throws Exception {
            UUID entityId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            Object problematicPayload = new Object();

            when(jsonMapper.writeValueAsString(problematicPayload)).thenThrow(mock(JacksonException.class));

            auditService.log("INVOICE", entityId, "INVOICE_UPDATED", problematicPayload, null, userId);

            ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
            verify(auditLogRepository).save(captor.capture());
            assertThat(captor.getValue().getOldValue()).isEqualTo("{\"error\":\"serialization_failed\"}");
        }

        @Test
        @DisplayName("should catch database mapping errors and prevent application task thread termination crashes")
        void should_suppress_and_log_global_pipeline_exceptions_internally() {
            UUID entityId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();

            when(auditLogRepository.save(any(AuditLog.class))).thenThrow(new RuntimeException("Database Connection Timeout"));

            auditService.log("INVOICE", entityId, "INVOICE_UPDATED", null, null, userId);

            verify(auditLogRepository, times(1)).save(any(AuditLog.class));
        }
    }

    @Nested
    @DisplayName("HTTP Request Metadata Resolution Strategies")
    class RequestContextResolution {

        @Test
        @DisplayName("should parse tracking attributes cleanly from standard HTTP header request components")
        void should_extract_metadata_from_standard_request_headers() {
            UUID entityId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();

            HttpServletRequest request = mock(HttpServletRequest.class);
            when(request.getHeader("X-Forwarded-For")).thenReturn("192.168.1.50, 10.0.0.1");
            when(request.getHeader("X-Request-ID")).thenReturn("REQ-TEST-123");

            ServletRequestAttributes attributes = new ServletRequestAttributes(request);
            RequestContextHolder.setRequestAttributes(attributes);

            auditService.log("INVOICE", entityId, "INVOICE_UPDATED", null, null, userId);

            ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
            verify(auditLogRepository).save(captor.capture());

            assertThat(captor.getValue().getIpAddress()).isEqualTo("192.168.1.50");
            assertThat(captor.getValue().getRequestId()).isEqualTo("REQ-TEST-123");
        }

        @Test
        @DisplayName("should fall back to raw network attributes when X-Forwarded-For is blank and derive substring session tracking signatures")
        void should_fallback_to_remote_address_and_session_substrings() {
            UUID entityId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();

            HttpServletRequest request = mock(HttpServletRequest.class);
            HttpSession session = mock(HttpSession.class);

            when(request.getHeader("X-Forwarded-For")).thenReturn("   ");
            when(request.getRemoteAddr()).thenReturn("127.0.0.1");
            when(request.getHeader("X-Request-ID")).thenReturn("");
            when(request.getSession(false)).thenReturn(session);
            when(session.getId()).thenReturn("SESSION_TOKEN_LONG_STRING_SIGNATURE");

            ServletRequestAttributes attributes = new ServletRequestAttributes(request);
            RequestContextHolder.setRequestAttributes(attributes);

            auditService.log("INVOICE", entityId, "INVOICE_UPDATED", null, null, userId);

            ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
            verify(auditLogRepository).save(captor.capture());

            assertThat(captor.getValue().getIpAddress()).isEqualTo("127.0.0.1");
            assertThat(captor.getValue().getRequestId()).isEqualTo("SESSION_");
        }
    }
}