package com.invoiceapp.backend.shared.exception;

import com.invoiceapp.backend.client.domain.Client;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("GlobalExceptionHandler")
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("should return ProblemDetail with correct status for InvoiceAppException")
    void should_handle_invoice_app_exception() {
        InvoiceAppException ex = new InvoiceAppException("Invoice not found", HttpStatus.NOT_FOUND);

        ProblemDetail detail = handler.handleInvoiceAppException(ex);

        assertThat(detail.getStatus()).isEqualTo(404);
        assertThat(detail.getDetail()).isEqualTo("Invoice not found");
        assertThat(detail.getTitle()).isEqualTo("Not Found");
    }

    @Test
    @DisplayName("should return 400 with field errors for validation exception")
    void should_handle_validation_exception() {
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        BindingResult bindingResult = mock(BindingResult.class);

        when(ex.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getFieldErrors()).thenReturn(List.of(
                new FieldError("invoiceRequest", "clientId",
                        "Client is required"),
                new FieldError("invoiceRequest", "dueDate",
                        "Due date is required")
        ));

        ProblemDetail detail = handler.handleValidationException(ex);

        assertThat(detail.getStatus()).isEqualTo(400);
        assertThat(detail.getTitle()).isEqualTo("Validation failed");

        @SuppressWarnings("unchecked")
        var errors = (java.util.Map<String, String>) detail.getProperties().get("errors");
        assertThat(errors)
                .containsEntry("clientId", "Client is required")
                .containsEntry("dueDate", "Due date is required");
    }

    @Test
    @DisplayName("should return 401 with vague message for bad credentials")
    void should_handle_bad_credentials_with_vague_message() {
        BadCredentialsException ex = new BadCredentialsException("Bad credentials");

        ProblemDetail detail = handler.handleBadCredentials(ex);

        assertThat(detail.getStatus()).isEqualTo(401);
        assertThat(detail.getDetail())
                .isEqualTo("Invalid email or password")
                .doesNotContain("not found");
    }

    @Test
    @DisplayName("should return 500 without leaking internal details")
    void should_handle_generic_exception_without_leaking_details() {
        Exception ex = new RuntimeException("org.postgresql.util.PSQLException: ERROR: relation does not exist");

        ProblemDetail detail = handler.handleGenericException(ex);

        assertThat(detail.getStatus()).isEqualTo(500);
        assertThat(detail.getDetail())
                .doesNotContain("PSQLException")
                .doesNotContain("postgresql")
                .isEqualTo("An unexpected error occurred");
    }

    @Test
    @DisplayName("should return 409 Conflict for DataIntegrityViolationException")
    void should_handle_data_integrity_violation() {
        org.springframework.dao.DataIntegrityViolationException ex =
                new org.springframework.dao.DataIntegrityViolationException("Foreign key violation");

        ProblemDetail detail = handler.handleDataIntegrity(ex);

        assertThat(detail.getStatus()).isEqualTo(409);
        assertThat(detail.getTitle()).isEqualTo("Data integrity violation");
        assertThat(detail.getDetail()).contains("Ensure all related records are removed first.");
    }

    @Test
    @DisplayName("should handle async request timeout silently")
    void should_handle_async_timeout_silently() {
        assertThatNoException().isThrownBy(handler::handleAsyncTimeout);
    }

    @Test
    @DisplayName("should handle IOException branch where message contains Broken pipe")
    void should_handle_io_exception_with_broken_pipe() {
        java.io.IOException ex = new java.io.IOException("Write failed: Broken pipe");
        assertThatNoException().isThrownBy(() -> handler.handleIOException(ex));
    }

    @Test
    @DisplayName("should handle IOException branches where message is null or doesn't contain Broken pipe")
    void should_handle_io_exception_other_scenarios() {
        java.io.IOException nullMessageEx = new java.io.IOException((String) null);
        assertThatNoException().isThrownBy(() -> handler.handleIOException(nullMessageEx));

        java.io.IOException otherEx = new java.io.IOException("Connection refused");
        assertThatNoException().isThrownBy(() -> handler.handleIOException(otherEx));
    }

    @Test
    @DisplayName("should return 409 Conflict with OPTIMISTIC_LOCK_FAILURE property on concurrent modification")
    void should_handle_optimistic_lock_exception() {
        org.springframework.orm.ObjectOptimisticLockingFailureException ex =
                new ObjectOptimisticLockingFailureException(Client.class, UUID.randomUUID());

        ProblemDetail detail = handler.handleOptimisticLock(ex);

        assertThat(detail.getStatus()).isEqualTo(409);
        assertThat(detail.getTitle()).isEqualTo("Concurrent modification");
        assertThat(detail.getDetail()).contains("modified by another request");

        assertThat(detail.getProperties())
                .isNotNull()
                .containsEntry("type", "OPTIMISTIC_LOCK_FAILURE");
    }

    @Test
    @DisplayName("should return 400 Bad Request with precise message for missing query parameter")
    void should_handle_missing_servlet_request_parameter_exception() {
        org.springframework.web.bind.MissingServletRequestParameterException ex =
                new MissingServletRequestParameterException("clientId", "String");

        ProblemDetail detail = handler.handleMissingParams(ex);

        assertThat(detail.getStatus()).isEqualTo(400);
        assertThat(detail.getTitle()).isEqualTo("Missing request parameter");
        assertThat(detail.getDetail()).isEqualTo("Required query parameter 'clientId' is missing.");
    }

    @Test
    @DisplayName("should return 403 Forbidden when an AuthorizationDeniedException is thrown")
    void should_handle_authorization_denied_exception() {
        AuthorizationDeniedException ex = new AuthorizationDeniedException("Access Denied");

        ProblemDetail detail = handler.handleAuthorizationDeniedException(ex);

        Assertions.assertThat(detail.getStatus()).isEqualTo(403);
        Assertions.assertThat(detail.getTitle()).isEqualTo("Access Denied");
        Assertions.assertThat(detail.getDetail()).isEqualTo("You do not have permission to access this resource.");
    }
}