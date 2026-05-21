package com.invoiceapp.backend.shared.exception;

import jakarta.persistence.OptimisticLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {
    // Handles our InvoiceAppException
    @ExceptionHandler(InvoiceAppException.class)
    public ProblemDetail handleInvoiceAppException(InvoiceAppException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(ex.getStatus(), ex.getMessage());
        detail.setTitle(ex.getStatus().getReasonPhrase());
        return detail;
    }

    // @Valid failed on a @RequestBody.
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidationException(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            errors.put(error.getField(), error.getDefaultMessage());
        }

        ProblemDetail detail = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        detail.setTitle("Validation failed");
        detail.setProperty("errors", errors);
        return detail;
    }

    // Login email/password is wrong
    @ExceptionHandler(BadCredentialsException.class)
    public ProblemDetail handleBadCredentials(BadCredentialsException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNAUTHORIZED, "Invalid email or password"
        );
        detail.setTitle("Unauthorized");
        return detail;
    }

    // Thrown by PostgreSQL when a constraint is violated
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleDataIntegrity(DataIntegrityViolationException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT,
                "This operation conflicts with existing data. " +
                        "Ensure all related records are removed first."
        );
        detail.setTitle("Data integrity violation");
        return detail;
    }

    // Handle SSE Timeouts silently
    @ExceptionHandler(org.springframework.web.context.request.async.AsyncRequestTimeoutException.class)
    public void handleAsyncTimeout() {
    }

    // Handle Browser Tab closures silently
    @ExceptionHandler(java.io.IOException.class)
    public void handleIOException(java.io.IOException ex) {
        if (ex.getMessage() != null && ex.getMessage().contains("Broken pipe")) {
            return;
        }
    }

    @ExceptionHandler({OptimisticLockException.class, ObjectOptimisticLockingFailureException.class})
    public ProblemDetail handleOptimisticLock(Exception ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT,
                "This record was modified by another request. " +
                        "Please refresh and try again."
        );
        detail.setTitle("Concurrent modification");
        detail.setProperty("type", "OPTIMISTIC_LOCK_FAILURE");
        return detail;
    }

    // Missing query parameters
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ProblemDetail handleMissingParams(MissingServletRequestParameterException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Required query parameter '%s' is missing.", ex.getParameterName())
        );
        detail.setTitle("Missing request parameter");
        return detail;
    }

    @ExceptionHandler(AuthorizationDeniedException.class)
    public ProblemDetail handleAuthorizationDeniedException(AuthorizationDeniedException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(
                HttpStatus.FORBIDDEN, "You do not have permission to access this resource.");
        detail.setTitle("Access Denied");
        return detail;
    }

    // Catch-all exception
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGenericException(Exception ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred"
        );
        detail.setTitle("Internal server error");
        return detail;
    }
}