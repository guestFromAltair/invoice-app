package com.invoiceapp.backend.invoice.service;

import com.invoiceapp.backend.auth.domain.User;
import com.invoiceapp.backend.auth.domain.UserRepository;
import com.invoiceapp.backend.invoice.domain.*;
import com.invoiceapp.backend.shared.exception.InvoiceAppException;
import com.invoiceapp.backend.shared.metrics.InvoiceMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentService")
class PaymentServiceTest {

    @Mock private PaymentRepository paymentRepository;
    @Mock private InvoiceRepository invoiceRepository;
    @Mock private UserRepository userRepository;
    @Mock private InvoiceMetrics invoiceMetrics;
    @Mock private InvoiceService invoiceService;

    @InjectMocks
    private PaymentService paymentService;

    private Invoice testInvoice;
    private UUID userId;
    private UUID invoiceId;

    @BeforeEach
    void setUp() {
        userId    = UUID.randomUUID();
        invoiceId = UUID.randomUUID();

        User testUser = User.builder()
                .id(userId)
                .email("test@example.com")
                .password("hashed")
                .role(com.invoiceapp.backend.auth.domain.Role.USER)
                .build();

        testInvoice = Invoice.builder()
                .id(invoiceId)
                .invoiceNumber("INV-2024-00001")
                .createdBy(testUser)
                .status(InvoiceStatus.SENT)
                .total(new BigDecimal("1000.0000"))
                .issueDate(LocalDate.now())
                .dueDate(LocalDate.now().plusDays(30))
                .lineItems(new ArrayList<>())
                .payments(new ArrayList<>())
                .build();

        Authentication authentication = mock(Authentication.class);
        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getName()).thenReturn("test@example.com");
        SecurityContextHolder.setContext(securityContext);

        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
    }

    @Test
    @DisplayName("should record a partial payment and leave invoice as SENT")
    void should_record_partial_payment_and_leave_status_as_sent() {
        when(invoiceRepository.findByIdAndCreatedById(invoiceId, userId)).thenReturn(Optional.of(testInvoice));
        when(paymentRepository.sumAmountByInvoiceId(invoiceId)).thenReturn(BigDecimal.ZERO);
        when(paymentRepository.save(any())).thenAnswer(invocation -> {
            Payment payment = invocation.getArgument(0);
            payment.setId(UUID.randomUUID());
            return payment;
        });
        when(invoiceService.computeTotalOutstandingBalance()).thenReturn(500.0);

        PaymentService.PaymentResponse response = paymentService.recordPayment(
                invoiceId,
                new PaymentService.PaymentRequest(
                        new BigDecimal("500.00"), null, "BANK_TRANSFER", null
                )
        );

        assertThat(response.amount()).isEqualByComparingTo(new BigDecimal("500.00"));
        // In-memory assertion — testInvoice is the same object reference as the mock returned to the service
        assertThat(testInvoice.getStatus()).isEqualTo(InvoiceStatus.SENT);
    }

    @Test
    @DisplayName("should auto-mark invoice as PAID when balance reaches zero")
    void should_auto_mark_paid_when_balance_reaches_zero() {
        when(invoiceRepository.findByIdAndCreatedById(invoiceId, userId)).thenReturn(Optional.of(testInvoice));
        when(paymentRepository.sumAmountByInvoiceId(invoiceId)).thenReturn(new BigDecimal("600.00"));
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(invoiceService.computeTotalOutstandingBalance()).thenReturn(0.0);

        paymentService.recordPayment(
                invoiceId,
                new PaymentService.PaymentRequest(
                        new BigDecimal("400.00"), null, "BANK_TRANSFER", null
                )
        );

        assertThat(testInvoice.getStatus()).isEqualTo(InvoiceStatus.PAID);
    }

    @Test
    @DisplayName("should reject payment exceeding remaining balance")
    void should_reject_overpayment() {
        when(invoiceRepository.findByIdAndCreatedById(invoiceId, userId)).thenReturn(Optional.of(testInvoice));
        when(paymentRepository.sumAmountByInvoiceId(invoiceId)).thenReturn(BigDecimal.ZERO);

        assertThatThrownBy(() -> paymentService.recordPayment(
                invoiceId,
                new PaymentService.PaymentRequest(
                        new BigDecimal("1500.00"), null, null, null
                )
        ))
                .isInstanceOf(InvoiceAppException.class)
                .hasMessageContaining("exceeds remaining balance");
    }

    @Test
    @DisplayName("should reject payment on a DRAFT invoice")
    void should_reject_payment_on_draft_invoice() {
        testInvoice.setStatus(InvoiceStatus.DRAFT);
        when(invoiceRepository.findByIdAndCreatedById(invoiceId, userId)).thenReturn(Optional.of(testInvoice));

        assertThatThrownBy(() -> paymentService.recordPayment(
                invoiceId,
                new PaymentService.PaymentRequest(
                        new BigDecimal("100.00"), null, null, null
                )
        ))
                .isInstanceOf(InvoiceAppException.class)
                .hasMessageContaining("SENT or OVERDUE");
    }

    @Test
    @DisplayName("should reject zero amount payment")
    void should_reject_zero_amount_payment() {
        when(invoiceRepository.findByIdAndCreatedById(invoiceId, userId)).thenReturn(Optional.of(testInvoice));
        when(paymentRepository.sumAmountByInvoiceId(invoiceId)).thenReturn(BigDecimal.ZERO);

        assertThatThrownBy(() -> paymentService.recordPayment(
                invoiceId,
                new PaymentService.PaymentRequest(
                        BigDecimal.ZERO, null, null, null
                )
        ))
                .isInstanceOf(InvoiceAppException.class)
                .hasMessageContaining("greater than zero");
    }
}