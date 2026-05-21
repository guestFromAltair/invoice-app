package com.invoiceapp.backend.invoice.service;

import com.invoiceapp.backend.auth.domain.User;
import com.invoiceapp.backend.invoice.domain.*;
import com.invoiceapp.backend.shared.audit.AuditAction;
import com.invoiceapp.backend.shared.audit.AuditService;
import com.invoiceapp.backend.shared.exception.InvoiceAppException;
import com.invoiceapp.backend.shared.metrics.InvoiceMetrics;
import com.invoiceapp.backend.shared.security.CurrentUserResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentService")
class PaymentServiceTest {

    @Mock private PaymentRepository paymentRepository;
    @Mock private InvoiceRepository invoiceRepository;
    @Mock private CurrentUserResolver currentUserResolver;
    @Mock private InvoiceMetrics invoiceMetrics;
    @Mock private InvoiceService invoiceService;
    @Mock private AuditService auditService;

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
                .invoiceNumber("INV-2026-00001")
                .createdBy(testUser)
                .status(InvoiceStatus.SENT)
                .total(new BigDecimal("1000.0000"))
                .issueDate(LocalDate.now())
                .dueDate(LocalDate.now().plusDays(30))
                .lineItems(new ArrayList<>())
                .payments(new ArrayList<>())
                .build();

        lenient().when(currentUserResolver.resolveUser()).thenReturn(testUser);
    }

    @Test
    @DisplayName("should record a partial payment, leave invoice as SENT, and write standard audit event")
    void should_record_partial_payment_and_leave_status_as_sent() {
        UUID paymentId = UUID.randomUUID();
        when(invoiceRepository.findByIdAndCreatedById(invoiceId, userId)).thenReturn(Optional.of(testInvoice));
        when(paymentRepository.sumAmountByInvoiceId(invoiceId)).thenReturn(BigDecimal.ZERO);
        when(paymentRepository.save(any())).thenAnswer(invocation -> {
            Payment payment = invocation.getArgument(0);
            payment.setId(paymentId);
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
        assertThat(testInvoice.getStatus()).isEqualTo(InvoiceStatus.SENT);

        verify(auditService, times(1)).log(
                eq("PAYMENT"),
                eq(paymentId),
                eq(AuditAction.PAYMENT_RECORDED),
                isNull(),
                eq(Map.of(
                        "invoiceId", invoiceId.toString(),
                        "amount", "500.00",
                        "method", "BANK_TRANSFER"
                )),
                eq(userId)
        );
    }

    @Test
    @DisplayName("should auto-mark invoice as PAID and capture fallbacks when method isn't explicitly provided")
    void should_auto_mark_paid_when_balance_reaches_zero() {
        UUID paymentId = UUID.randomUUID();
        when(invoiceRepository.findByIdAndCreatedById(invoiceId, userId)).thenReturn(Optional.of(testInvoice));
        when(paymentRepository.sumAmountByInvoiceId(invoiceId)).thenReturn(new BigDecimal("600.00"));
        when(paymentRepository.save(any())).thenAnswer(invocation -> {
            Payment payment = invocation.getArgument(0);
            payment.setId(paymentId);
            return payment;
        });
        when(invoiceService.computeTotalOutstandingBalance()).thenReturn(0.0);

        paymentService.recordPayment(
                invoiceId,
                new PaymentService.PaymentRequest(
                        new BigDecimal("400.00"), null, null, null
                )
        );

        assertThat(testInvoice.getStatus()).isEqualTo(InvoiceStatus.PAID);

        verify(auditService, times(1)).log(
                eq("PAYMENT"),
                eq(paymentId),
                eq(AuditAction.PAYMENT_RECORDED),
                isNull(),
                eq(Map.of(
                        "invoiceId", invoiceId.toString(),
                        "amount", "400.00",
                        "method", "UNSPECIFIED"
                )),
                eq(userId)
        );
    }

    @Test
    @DisplayName("should reject payment exceeding remaining balance and bypass audit trail pipelines")
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

        verifyNoInteractions(auditService);
    }

    @Test
    @DisplayName("should reject payment on a DRAFT invoice and bypass audit updates")
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

        verifyNoInteractions(auditService);
    }

    @Test
    @DisplayName("should reject zero amount payment and bypass audit log engine paths completely")
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

        verifyNoInteractions(auditService);
    }

    @Nested
    @DisplayName("Invoice History Queries")
    class InvoiceHistoryQueries {

        @Test
        @DisplayName("should return payment history list when authorized user queries")
        void should_return_payment_history_when_authorized() {
            when(invoiceRepository.findByIdAndCreatedById(invoiceId, userId)).thenReturn(Optional.of(testInvoice));

            Payment payment = Payment.builder()
                    .id(UUID.randomUUID())
                    .amount(new BigDecimal("250.0000"))
                    .paidAt(Instant.now())
                    .method("CREDIT_CARD")
                    .notes("Partial payment")
                    .createdAt(Instant.now())
                    .build();

            when(paymentRepository.findAllByInvoiceId(invoiceId)).thenReturn(List.of(payment));

            List<PaymentService.PaymentResponse> history = paymentService.findAllByInvoice(invoiceId);

            assertThat(history).hasSize(1);
            assertThat(history.getFirst().amount()).isEqualByComparingTo("250.00");
        }

        @Test
        @DisplayName("should throw 404 error on payment history lookup if invoice does not exist")
        void should_throw_404_when_invoice_missing_during_history_lookup() {
            when(invoiceRepository.findByIdAndCreatedById(invoiceId, userId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> paymentService.findAllByInvoice(invoiceId))
                    .isInstanceOf(InvoiceAppException.class)
                    .hasMessageContaining("Invoice not found");

            verifyNoInteractions(paymentRepository);
        }
    }
}