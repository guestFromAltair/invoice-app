package com.invoiceapp.backend.shared.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
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
}