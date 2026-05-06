package com.invoiceapp.backend.pdf.service;

import com.invoiceapp.backend.auth.domain.Role;
import com.invoiceapp.backend.auth.domain.User;
import com.invoiceapp.backend.client.domain.Client;
import com.invoiceapp.backend.invoice.domain.*;
import com.invoiceapp.backend.shared.exception.InvoiceAppException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PdfGenerationService")
class PdfGenerationServiceTest {

    @Mock
    private InvoiceRepository invoiceRepository;
    @Mock
    private PaymentRepository paymentRepository;

    @InjectMocks
    private PdfGenerationService pdfGenerationService;

    @Test
    @DisplayName("should generate a non-empty PDF byte array for a valid invoice")
    void should_generate_non_empty_pdf_for_valid_invoice() {
        UUID invoiceId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Invoice invoice = buildFullInvoice(invoiceId, userId);

        when(invoiceRepository.findByIdAndCreatedById(invoiceId, userId)).thenReturn(Optional.of(invoice));
        when(paymentRepository.findAllByInvoiceId(invoiceId)).thenReturn(List.of());

        byte[] pdf = pdfGenerationService.generateInvoicePdf(invoiceId, userId);

        assertThat(pdf).isNotEmpty();
        assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");
    }

    @Test
    @DisplayName("should generate PDF including payment history")
    void should_generate_pdf_with_payment_history() {
        UUID invoiceId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Invoice invoice = buildFullInvoice(invoiceId, userId);

        Payment payment = Payment.builder()
                .id(UUID.randomUUID())
                .invoice(invoice)
                .amount(new BigDecimal("1000.0000"))
                .method("BANK_TRANSFER")
                .notes("First instalment")
                .build();

        when(invoiceRepository.findByIdAndCreatedById(invoiceId, userId)).thenReturn(Optional.of(invoice));
        when(paymentRepository.findAllByInvoiceId(invoiceId)).thenReturn(List.of(payment));

        assertThatNoException().isThrownBy(() ->
                pdfGenerationService.generateInvoicePdf(invoiceId, userId)
        );
    }

    @Test
    @DisplayName("should throw 404 when invoice not found")
    void should_throw_404_when_invoice_not_found() {
        UUID invoiceId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        when(invoiceRepository.findByIdAndCreatedById(invoiceId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                pdfGenerationService.generateInvoicePdf(invoiceId, userId)
        )
                .isInstanceOf(InvoiceAppException.class)
                .hasMessageContaining("not found");
    }

    private Invoice buildFullInvoice(UUID invoiceId, UUID userId) {
        User user = User.builder()
                .id(userId)
                .email("test@example.com")
                .role(Role.USER)
                .password("hashed")
                .build();

        Client client = Client.builder()
                .id(UUID.randomUUID())
                .owner(user)
                .name("Acme Corp")
                .email("billing@acme.com")
                .address("12 Rue de Rivoli, Paris")
                .vatNumber("FR12345678901")
                .build();

        LineItem lineItem = LineItem.builder()
                .id(UUID.randomUUID())
                .description("Frontend development")
                .quantity(new BigDecimal("10"))
                .unitPrice(new BigDecimal("150.00"))
                .discountPct(BigDecimal.ZERO)
                .lineTotal(new BigDecimal("1500.0000"))
                .position(1)
                .build();

        Invoice invoice = Invoice.builder()
                .id(invoiceId)
                .invoiceNumber("INV-2024-00001")
                .client(client)
                .createdBy(user)
                .status(InvoiceStatus.SENT)
                .issueDate(LocalDate.of(2024, 1, 15))
                .dueDate(LocalDate.of(2024, 2, 15))
                .subtotal(new BigDecimal("1500.0000"))
                .taxRate(new BigDecimal("0.2000"))
                .taxAmount(new BigDecimal("300.0000"))
                .total(new BigDecimal("1800.0000"))
                .lineItems(new ArrayList<>(List.of(lineItem)))
                .payments(new ArrayList<>())
                .notes("Web development services")
                .build();

        lineItem.setInvoice(invoice);
        return invoice;
    }
}