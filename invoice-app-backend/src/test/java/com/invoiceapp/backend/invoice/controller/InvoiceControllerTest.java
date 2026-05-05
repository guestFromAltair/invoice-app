package com.invoiceapp.backend.invoice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.invoiceapp.backend.auth.domain.UserRepository;
import com.invoiceapp.backend.auth.service.JwtService;
import com.invoiceapp.backend.invoice.domain.InvoiceStatus;
import com.invoiceapp.backend.invoice.service.InvoiceService;
import com.invoiceapp.backend.pdf.service.PdfGenerationService;
import com.invoiceapp.backend.shared.exception.InvoiceAppException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

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
        InvoiceService.InvoiceResponse mockResponse = buildMockResponse(id);

        when(invoiceService.findById(id)).thenReturn(mockResponse);

        mockMvc.perform(get("/api/invoices/{id}", id))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.invoiceNumber").value("INV-2024-00001"))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.total").value(3312.0));
    }

    @Test
    @WithMockUser
    @DisplayName("GET /api/invoices/{id} should return 404 when not found")
    void get_invoice_by_id_returns_404_when_not_found() throws Exception {
        UUID id = UUID.randomUUID();
        when(invoiceService.findById(id))
                .thenThrow(new InvoiceAppException(
                        "Invoice not found", HttpStatus.NOT_FOUND
                ));

        mockMvc.perform(get("/api/invoices/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Invoice not found"));
    }

    @Test
    @WithMockUser
    @DisplayName("POST /api/invoices/{id}/send should return 422 for illegal transition")
    void send_invoice_returns_422_for_illegal_transition() throws Exception {
        UUID id = UUID.randomUUID();
        when(invoiceService.send(id))
                .thenThrow(new InvoiceAppException(
                        "Cannot transition invoice from PAID to SENT",
                        HttpStatus.UNPROCESSABLE_CONTENT
                ));

        mockMvc.perform(post("/api/invoices/{id}/send", id)
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

    private InvoiceService.InvoiceResponse buildMockResponse(UUID id) {
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
                Instant.parse("2024-01-15T10:00:00Z")
        );
    }
}