package com.invoiceapp.backend.invoice.controller;

import com.invoiceapp.backend.auth.domain.UserRepository;
import com.invoiceapp.backend.auth.service.JwtService;
import com.invoiceapp.backend.invoice.service.PaymentService;
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
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PaymentController.class)
@DisplayName("PaymentController")
class PaymentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PaymentService paymentService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private UserRepository userRepository;

    @Test
    @WithMockUser
    @DisplayName("GET /api/invoices/{id}/payments should return list of payments")
    void find_all_returns_200_and_list() throws Exception {
        UUID invoiceId = UUID.randomUUID();
        PaymentService.PaymentResponse mockPayment = new PaymentService.PaymentResponse(
                UUID.randomUUID(),
                new BigDecimal("500.00"),
                Instant.now(),
                "STRIPE",
                "Partial payment",
                Instant.now()
        );

        when(paymentService.findAllByInvoice(invoiceId)).thenReturn(List.of(mockPayment));

        mockMvc.perform(get("/api/invoices/{invoiceId}/payments", invoiceId))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].amount").value(500.00))
                .andExpect(jsonPath("$[0].method").value("STRIPE"));
    }

    @Test
    @WithMockUser
    @DisplayName("POST /api/invoices/{id}/payments should return 201 when valid")
    void record_payment_returns_201_on_success() throws Exception {
        UUID invoiceId = UUID.randomUUID();
        String jsonBody = """
                {
                    "amount": 250.50,
                    "paidAt": "2024-05-20T10:00:00Z",
                    "method": "CASH",
                    "notes": "Handled in person"
                }
                """;

        PaymentService.PaymentResponse mockResponse = new PaymentService.PaymentResponse(
                UUID.randomUUID(),
                new BigDecimal("250.50"),
                Instant.parse("2024-05-20T10:00:00Z"),
                "CASH",
                "Handled in person",
                Instant.now()
        );

        when(paymentService.recordPayment(eq(invoiceId), any(PaymentService.PaymentRequest.class)))
                .thenReturn(mockResponse);

        mockMvc.perform(post("/api/invoices/{invoiceId}/payments", invoiceId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.amount").value(250.50))
                .andExpect(jsonPath("$.method").value("CASH"));
    }

    @Test
    @WithMockUser
    @DisplayName("POST /api/invoices/{id}/payments should return 422 if amount exceeds balance")
    void record_payment_returns_422_when_exceeding_balance() throws Exception {
        UUID invoiceId = UUID.randomUUID();
        String jsonBody = """
                {
                    "amount": 99999.99,
                    "method": "WIRE"
                }
                """;

        when(paymentService.recordPayment(eq(invoiceId), any(PaymentService.PaymentRequest.class)))
                .thenThrow(new InvoiceAppException(
                        "Payment amount exceeds remaining balance",
                        HttpStatus.UNPROCESSABLE_CONTENT
                ));

        mockMvc.perform(post("/api/invoices/{invoiceId}/payments", invoiceId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value("Payment amount exceeds remaining balance"));
    }

    @Test
    @WithMockUser
    @DisplayName("POST /api/invoices/{id}/payments should return 400 for negative amount")
    void record_payment_returns_400_for_invalid_input() throws Exception {
        UUID invoiceId = UUID.randomUUID();
        String invalidBody = """
                {
                    "amount": -10.00
                }
                """;

        mockMvc.perform(post("/api/invoices/{invoiceId}/payments", invoiceId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.amount").exists());
    }
}