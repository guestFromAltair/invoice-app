package com.invoiceapp.backend.invoice.service;

import com.invoiceapp.backend.auth.domain.User;
import com.invoiceapp.backend.auth.domain.UserRepository;
import com.invoiceapp.backend.client.domain.Client;
import com.invoiceapp.backend.client.domain.ClientRepository;
import com.invoiceapp.backend.invoice.domain.*;
import com.invoiceapp.backend.notification.controller.NotificationController;
import com.invoiceapp.backend.shared.exception.InvoiceAppException;
import com.invoiceapp.backend.shared.metrics.InvoiceMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("InvoiceService")
class InvoiceServiceTest {

    @Mock
    private InvoiceRepository invoiceRepository;
    @Mock
    private ClientRepository clientRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private InvoiceMetrics invoiceMetrics;
    @Mock
    private NotificationController notificationController;

    @InjectMocks
    private InvoiceService invoiceService;
    private User testUser;
    private Client testClient;
    private UUID userId;
    private UUID clientId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        clientId = UUID.randomUUID();

        testUser = User.builder()
                .id(userId)
                .email("test@example.com")
                .password("hashed")
                .role(com.invoiceapp.backend.auth.domain.Role.USER)
                .build();

        testClient = Client.builder()
                .id(clientId)
                .owner(testUser)
                .name("Acme Corp")
                .email("billing@acme.com")
                .build();

        Authentication authentication = mock(Authentication.class);
        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getName()).thenReturn("test@example.com");
        SecurityContextHolder.setContext(securityContext);

        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
    }

    @Nested
    @DisplayName("invoice state transitions")
    class StateTransitions {

        @Test
        @DisplayName("should transition from DRAFT to SENT successfully")
        void should_transition_draft_to_sent() {
            Invoice invoice = buildInvoice(InvoiceStatus.DRAFT);
            when(invoiceRepository.findByIdAndCreatedById(invoice.getId(), userId)).thenReturn(Optional.of(invoice));

            InvoiceService.InvoiceResponse response = invoiceService.send(invoice.getId());

            assertThat(response.status()).isEqualTo(InvoiceStatus.SENT);
        }

        @Test
        @DisplayName("should throw when transitioning PAID invoice to any status")
        void should_throw_when_transitioning_paid_invoice() {
            Invoice invoice = buildInvoice(InvoiceStatus.PAID);
            when(invoiceRepository.findByIdAndCreatedById(invoice.getId(), userId)).thenReturn(Optional.of(invoice));

            assertThatThrownBy(() -> invoiceService.send(invoice.getId()))
                    .isInstanceOf(InvoiceAppException.class)
                    .hasMessageContaining("Cannot transition invoice from PAID");
        }

        @Test
        @DisplayName("should throw when transitioning CANCELLED invoice")
        void should_throw_when_transitioning_cancelled_invoice() {
            Invoice invoice = buildInvoice(InvoiceStatus.CANCELLED);
            when(invoiceRepository.findByIdAndCreatedById(invoice.getId(), userId)).thenReturn(Optional.of(invoice));

            assertThatThrownBy(() -> invoiceService.send(invoice.getId()))
                    .isInstanceOf(InvoiceAppException.class)
                    .hasMessageContaining("Cannot transition invoice from CANCELLED");
        }

        @Test
        @DisplayName("should throw 404 when invoice not found")
        void should_throw_404_when_invoice_not_found() {
            UUID randomId = UUID.randomUUID();
            when(invoiceRepository.findByIdAndCreatedById(randomId, userId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> invoiceService.send(randomId))
                    .isInstanceOf(InvoiceAppException.class)
                    .hasMessageContaining("Invoice not found");
        }

        @Test
        @DisplayName("should allow OVERDUE invoice to be marked PAID")
        void should_allow_overdue_invoice_to_be_marked_paid() {
            Invoice invoice = buildInvoice(InvoiceStatus.OVERDUE);

            when(invoiceRepository.findByIdAndCreatedById(invoice.getId(), userId)).thenReturn(Optional.of(invoice));

            when(paymentRepository.sumAmountByInvoiceId(invoice.getId())).thenReturn(new BigDecimal("3312.0000"));

            when(invoiceRepository.computeOutstandingBalance()).thenReturn(BigDecimal.ZERO);

            InvoiceService.InvoiceResponse response = invoiceService.markPaid(invoice.getId());

            assertThat(response.status()).isEqualTo(InvoiceStatus.PAID);
        }

        @Test
        @DisplayName("should record compensating payment when manually marking paid with balance remaining")
        void should_record_compensating_payment_when_marking_paid_with_remaining_balance() {
            Invoice invoice = buildInvoice(InvoiceStatus.SENT);

            when(invoiceRepository.findByIdAndCreatedById(invoice.getId(), userId)).thenReturn(Optional.of(invoice));
            when(paymentRepository.sumAmountByInvoiceId(invoice.getId())).thenReturn(BigDecimal.ZERO);

            when(invoiceRepository.computeOutstandingBalance()).thenReturn(BigDecimal.ZERO);

            InvoiceService.InvoiceResponse response = invoiceService.markPaid(invoice.getId());

            assertThat(response.status()).isEqualTo(InvoiceStatus.PAID);

            verify(paymentRepository, times(1)).save(argThat(payment ->
                    payment.getAmount().compareTo(new BigDecimal("3312.0000")) == 0
                            && "MANUAL_MARK_PAID".equals(payment.getMethod())
            ));
        }

        @Test
        @DisplayName("should not record compensating payment when balance is already zero")
        void should_not_record_compensating_payment_when_already_fully_paid() {
            Invoice invoice = buildInvoice(InvoiceStatus.SENT);

            when(invoiceRepository.findByIdAndCreatedById(invoice.getId(), userId)).thenReturn(Optional.of(invoice));
            when(paymentRepository.sumAmountByInvoiceId(invoice.getId())).thenReturn(new BigDecimal("3312.0000"));
            when(invoiceRepository.computeOutstandingBalance()).thenReturn(BigDecimal.ZERO);

            InvoiceService.InvoiceResponse response = invoiceService.markPaid(invoice.getId());

            assertThat(response.status()).isEqualTo(InvoiceStatus.PAID);

            verify(paymentRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("invoice total calculations")
    class Calculations {

        @Test
        @DisplayName("should calculate totals correctly with tax and discount")
        void should_calculate_totals_correctly() {
            when(clientRepository.findByIdAndOwnerId(clientId, userId)).thenReturn(Optional.of(testClient));
            when(invoiceRepository.nextInvoiceSequence()).thenReturn(1L);
            when(invoiceRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

            InvoiceService.InvoiceRequest request = new InvoiceService.InvoiceRequest(
                    clientId,
                    LocalDate.now(),
                    LocalDate.now().plusDays(30),
                    new BigDecimal("0.20"),
                    "Test invoice",
                    List.of(
                            new InvoiceService.LineItemRequest(
                                    "Frontend development",
                                    new BigDecimal("10"),
                                    new BigDecimal("150.00"),
                                    new BigDecimal("0.00"),
                                    1
                            ),
                            new InvoiceService.LineItemRequest(
                                    "Backend development",
                                    new BigDecimal("8"),
                                    new BigDecimal("175.00"),
                                    new BigDecimal("0.10"),
                                    2
                            )
                    )
            );


            InvoiceService.InvoiceResponse response = invoiceService.create(request);

            assertThat(response.subtotal()).isEqualByComparingTo(new BigDecimal("2760.0000"));
            assertThat(response.taxAmount()).isEqualByComparingTo(new BigDecimal("552.0000"));
            assertThat(response.total()).isEqualByComparingTo(new BigDecimal("3312.0000"));
        }

        @Test
        @DisplayName("should reject invoice when due date is before issue date")
        void should_reject_invoice_with_invalid_dates() {
            when(clientRepository.findByIdAndOwnerId(clientId, userId))
                    .thenReturn(Optional.of(testClient));

            InvoiceService.InvoiceRequest request = new InvoiceService.InvoiceRequest(
                    clientId,
                    LocalDate.of(2024, 2, 15),
                    LocalDate.of(2024, 1, 15),
                    BigDecimal.ZERO,
                    null,
                    List.of(new InvoiceService.LineItemRequest(
                            "Test", BigDecimal.ONE,
                            BigDecimal.TEN, BigDecimal.ZERO, 1))
            );

            assertThatThrownBy(() -> invoiceService.create(request))
                    .isInstanceOf(InvoiceAppException.class)
                    .hasMessageContaining("Due date cannot be before issue date");
        }
    }

    private Invoice buildInvoice(InvoiceStatus status) {
        return Invoice.builder()
                .id(UUID.randomUUID())
                .invoiceNumber("INV-2024-00001")
                .client(testClient)
                .createdBy(testUser)
                .status(status)
                .issueDate(LocalDate.now())
                .dueDate(LocalDate.now().plusDays(30))
                .subtotal(new BigDecimal("2760.0000"))
                .taxRate(new BigDecimal("0.2000"))
                .taxAmount(new BigDecimal("552.0000"))
                .total(new BigDecimal("3312.0000"))
                .lineItems(new ArrayList<>())
                .payments(new ArrayList<>())
                .build();
    }
}