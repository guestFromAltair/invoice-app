package com.invoiceapp.backend.invoice.controller;

import com.invoiceapp.backend.invoice.domain.InvoiceStatus;
import com.invoiceapp.backend.invoice.service.InvoiceService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/invoices")
@RequiredArgsConstructor
public class InvoiceController {

    private final InvoiceService invoiceService;

    @GetMapping
    public Page<InvoiceService.InvoiceResponse> findAll(
            @RequestParam(required = false) InvoiceStatus status,
            @RequestParam(required = false) UUID clientId,
            @PageableDefault(size = 20, sort = "createdAt",
                    direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return invoiceService.findAll(status, clientId, pageable);
    }

    @GetMapping("/{id}")
    public InvoiceService.InvoiceResponse findById(@PathVariable UUID id) {
        return invoiceService.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public InvoiceService.InvoiceResponse create(@Valid @RequestBody InvoiceRequest request) {
        return invoiceService.create(new InvoiceService.InvoiceRequest(
                request.clientId(),
                request.issueDate(),
                request.dueDate(),
                request.taxRate(),
                request.notes(),
                request.lineItems() == null ? List.of() :
                        request.lineItems().stream()
                        .map(li -> new InvoiceService.LineItemRequest(
                                li.description(),
                                li.quantity(),
                                li.unitPrice(),
                                li.discountPct(),
                                li.position()
                        ))
                        .toList()
        ));
    }

    @PutMapping("/{id}/line-items")
    public InvoiceService.InvoiceResponse updateLineItems(
            @PathVariable UUID id,
            @Valid @RequestBody List<LineItemRequest> lineItems
    ) {
        return invoiceService.updateLineItems(
                id,
                lineItems.stream()
                        .map(li -> new InvoiceService.LineItemRequest(
                                li.description(),
                                li.quantity(),
                                li.unitPrice(),
                                li.discountPct(),
                                li.position()
                        ))
                        .toList()
        );
    }

    @PostMapping("/{id}/send")
    public InvoiceService.InvoiceResponse send(@PathVariable UUID id) {
        return invoiceService.send(id);
    }

    @PostMapping("/{id}/cancel")
    public InvoiceService.InvoiceResponse cancel(@PathVariable UUID id) {
        return invoiceService.cancel(id);
    }

    @PostMapping("/{id}/mark-paid")
    public InvoiceService.InvoiceResponse markPaid(@PathVariable UUID id) {
        return invoiceService.markPaid(id);
    }

    public record LineItemRequest(
            @NotBlank(message = "Description is required")
            String description,

            @NotNull @Positive(message = "Quantity must be positive")
            BigDecimal quantity,

            @NotNull @Positive(message = "Unit price must be positive")
            BigDecimal unitPrice,

            @DecimalMin(value = "0.0") @DecimalMax(value = "1.0")
            BigDecimal discountPct,

            Integer position
    ) {}

    public record InvoiceRequest(
            @NotNull(message = "Client is required")
            UUID clientId,

            @NotNull(message = "Issue date is required")
            LocalDate issueDate,

            @NotNull(message = "Due date is required")
            LocalDate dueDate,

            @DecimalMin("0.0") @DecimalMax("1.0")
            BigDecimal taxRate,

            String notes,

            @NotEmpty(message = "At least one line item is required")
            List<LineItemRequest> lineItems
    ) {}
}