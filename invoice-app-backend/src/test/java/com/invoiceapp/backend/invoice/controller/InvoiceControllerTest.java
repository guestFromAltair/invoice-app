package com.invoiceapp.backend.invoice.controller;

import com.invoiceapp.backend.auth.domain.User;
import com.invoiceapp.backend.auth.domain.UserRepository;
import com.invoiceapp.backend.auth.service.JwtService;
import com.invoiceapp.backend.invoice.domain.Invoice;
import com.invoiceapp.backend.invoice.domain.InvoiceStatus;
import com.invoiceapp.backend.invoice.service.InvoiceService;
import com.invoiceapp.backend.pdf.service.PdfGenerationService;
import com.invoiceapp.backend.shared.exception.InvoiceAppException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(InvoiceController.class)
@DisplayName("InvoiceController")
class InvoiceControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    InvoiceService invoiceService;

    @MockitoBean
    PdfGenerationService pdfGenerationService;

    @MockitoBean
    JwtService jwtService;

    @MockitoBean
    UserRepository userRepository;

    @Test
    @WithMockUser(username = "test@example.com", roles = "USER")
    @DisplayName("GET /api/invoices/{id} should return 200 with invoice")
    void get_invoice_by_id_returns_200() throws Exception {
        UUID id = UUID.randomUUID();
        InvoiceService.InvoiceResponse mockResponse = buildMockResponse(id, 0L);

        when(invoiceService.findById(id)).thenReturn(mockResponse);

        mockMvc.perform(get("/api/invoices/{id}", id))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.invoiceNumber").value("INV-2024-00001"))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.version").value(0))
                .andExpect(jsonPath("$.total").value(3312.0));
    }

    @Test
    @WithMockUser
    @DisplayName("GET /api/invoices/{id} should return 404 when not found")
    void get_invoice_by_id_returns_404_when_not_found() throws Exception {
        UUID id = UUID.randomUUID();
        when(invoiceService.findById(id)).thenThrow(new InvoiceAppException("Invoice not found", HttpStatus.NOT_FOUND));

        mockMvc.perform(get("/api/invoices/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Invoice not found"));
    }

    @Test
    @WithMockUser
    @DisplayName("POST /api/invoices/{id}/send should return 422 for illegal transition")
    void send_invoice_returns_422_for_illegal_transition() throws Exception {
        UUID id = UUID.randomUUID();
        when(invoiceService.send(eq(id), eq(1L)))
                .thenThrow(new InvoiceAppException(
                        "Cannot transition invoice from PAID to SENT",
                        HttpStatus.UNPROCESSABLE_CONTENT
                ));

        mockMvc.perform(post("/api/invoices/{id}/send", id)
                        .param("version", "1")
                        .with(csrf()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail")
                        .value("Cannot transition invoice from PAID to SENT"));
    }

    @Test
    @DisplayName("GET /api/invoices should return 401 without authentication")
    void get_invoices_returns_401_without_auth() throws Exception {
        mockMvc.perform(get("/api/invoices")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    @DisplayName("POST /api/invoices should return 400 for invalid request body")
    void create_invoice_returns_400_for_invalid_body() throws Exception {
        String invalidBody = """
                {
                    "issueDate": "2024-01-15"
                }
                """;

        mockMvc.perform(post("/api/invoices")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.errors.clientId").exists())
                .andExpect(jsonPath("$.errors.lineItems").exists());
    }

    @Test
    @WithMockUser
    @DisplayName("GET /api/invoices should return 200 with page data")
    void findAll_returns_200_with_page() throws Exception {
        UUID clientId = UUID.randomUUID();
        when(invoiceService.findAll(
                        InvoiceStatus.DRAFT,
                        clientId,
                        org.springframework.data.domain.PageRequest.of(
                                0,
                                20,
                                Sort.by(Sort.Direction.DESC, "createdAt"))
                )
        ).thenReturn(org.springframework.data.domain.Page.empty());

        mockMvc.perform(get("/api/invoices")
                        .param("status", "DRAFT")
                        .param("clientId", clientId.toString()))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser
    @DisplayName("POST /api/invoices should return 201 when body is valid")
    void create_invoice_returns_201_on_valid_payload() throws Exception {
        UUID id = UUID.randomUUID();
        InvoiceService.InvoiceResponse mockResponse = buildMockResponse(id, 0L);
        String validBody = """
                {
                    "clientId": "%s",
                    "issueDate": "2026-05-13",
                    "dueDate": "2026-06-13",
                    "taxRate": 0.20,
                    "notes": "Valid invoice details",
                    "lineItems": [
                        {
                            "description": "Item 1",
                            "quantity": 2.0,
                            "unitPrice": 100.0,
                            "discountPct": 0.0,
                            "position": 1
                        }
                    ]
                }
                """.formatted(UUID.randomUUID());

        when(invoiceService.create(any(InvoiceService.InvoiceRequest.class))).thenReturn(mockResponse);

        mockMvc.perform(post("/api/invoices")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(id.toString()));
    }

    @Test
    @WithMockUser
    @DisplayName("PUT /api/invoices/{id} should return 200 on successful full update")
    void update_invoice_returns_200() throws Exception {
        UUID id = UUID.randomUUID();
        String fullUpdatePayload = """
                {
                    "version": 3,
                    "issueDate": "2026-05-19",
                    "dueDate": "2026-06-19",
                    "taxRate": 0.20,
                    "notes": "Updated terms",
                    "lineItems": [
                        {
                            "description": "Consulting Work",
                            "quantity": 10,
                            "unitPrice": 150.00,
                            "discountPct": 0.1,
                            "position": 0
                        }
                    ]
                }
                """;

        when(invoiceService.update(
                eq(id),
                eq(3L),
                any(LocalDate.class),
                any(LocalDate.class),
                any(BigDecimal.class),
                anyString(),
                anyList()
        )).thenReturn(buildMockResponse(id, 4L));

        mockMvc.perform(put("/api/invoices/{id}", id)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(fullUpdatePayload))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser
    @DisplayName("PUT /api/invoices/{id} should return 409 Conflict when out-of-sync")
    void update_invoice_returns_409_on_concurrency_failure() throws Exception {
        UUID id = UUID.randomUUID();
        String fullUpdatePayload = """
                {
                    "version": 2,
                    "issueDate": "2026-05-19",
                    "dueDate": "2026-06-19",
                    "taxRate": 0.20,
                    "notes": "Stale edit",
                    "lineItems": [
                        {
                            "description": "Consulting Work",
                            "quantity": 10,
                            "unitPrice": 150.00,
                            "discountPct": 0.1,
                            "position": 0
                        }
                    ]
                }
                """;

        when(invoiceService.update(
                eq(id),
                eq(2L),
                any(LocalDate.class),
                any(LocalDate.class),
                any(BigDecimal.class),
                anyString(),
                anyList()
        )).thenThrow(new ObjectOptimisticLockingFailureException(Invoice.class, id));

        mockMvc.perform(put("/api/invoices/{id}", id)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(fullUpdatePayload))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Concurrent modification"))
                .andExpect(jsonPath("$.type").value("OPTIMISTIC_LOCK_FAILURE"));
    }

    @Test
    @WithMockUser
    @DisplayName("POST state trigger routes should map successfully to service backends with versions")
    void state_transitions_return_200() throws Exception {
        UUID id = UUID.randomUUID();
        InvoiceService.InvoiceResponse mockResponse = buildMockResponse(id, 1L);

        when(invoiceService.cancel(id, 0L)).thenReturn(mockResponse);
        when(invoiceService.markPaid(id, 0L)).thenReturn(mockResponse);

        mockMvc.perform(post("/api/invoices/{id}/cancel", id).param("version", "0").with(csrf()))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/invoices/{id}/mark-paid", id).param("version", "0").with(csrf()))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser
    @DisplayName("POST /api/invoices/{id}/send should return 409 Conflict optimistic lock failure")
    void state_transitions_return_409_on_mismatch() throws Exception {
        UUID id = UUID.randomUUID();
        when(invoiceService.send(eq(id), eq(5L)))
                .thenThrow(new ObjectOptimisticLockingFailureException(Invoice.class, id));

        mockMvc.perform(post("/api/invoices/{id}/send", id).param("version", "5").with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("OPTIMISTIC_LOCK_FAILURE"));
    }

    @Test
    @WithMockUser(username = "exporter@example.com")
    @DisplayName("GET /api/invoices/{id}/pdf should correctly serve binary stream outputs")
    void downloadPdf_returns_binary_stream() throws Exception {
        UUID invoiceId = UUID.randomUUID();
        UUID mockUserId = UUID.randomUUID();
        User mockUser = User.builder()
                .id(mockUserId)
                .email("exporter@example.com")
                .build();

        byte[] samplePdfContent = "%PDF-1.4 mock content bytes".getBytes();

        when(userRepository.findByEmail("exporter@example.com")).thenReturn(Optional.of(mockUser));
        when(pdfGenerationService.generateInvoicePdf(invoiceId, mockUserId)).thenReturn(samplePdfContent);

        mockMvc.perform(get("/api/invoices/{id}/pdf", invoiceId))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"invoice-" + invoiceId + ".pdf\""))
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PDF_VALUE))
                .andExpect(content().bytes(samplePdfContent));
    }

    private InvoiceService.InvoiceResponse buildMockResponse(UUID id, Long targetVersion) {
        return new InvoiceService.InvoiceResponse(
                id,
                "INV-2024-00001",
                "Acme Corp",
                UUID.randomUUID(),
                InvoiceStatus.DRAFT,
                LocalDate.of(2024, 1, 15),
                LocalDate.of(2024, 2, 15),
                new BigDecimal("2760.0000"),
                new BigDecimal("0.2000"),
                new BigDecimal("552.0000"),
                new BigDecimal("3312.0000"),
                BigDecimal.ZERO,
                new BigDecimal("3312.0000"),
                "Test invoice",
                List.of(),
                Instant.parse("2024-01-15T10:00:00Z"),
                targetVersion
        );
    }
}