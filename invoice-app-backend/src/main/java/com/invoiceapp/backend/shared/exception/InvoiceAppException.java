package com.invoiceapp.backend.shared.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class InvoiceAppException extends RuntimeException {
    private final HttpStatus status;

    public InvoiceAppException(String message, HttpStatus status) {
        super(message);
        this.status = status;
    }
}