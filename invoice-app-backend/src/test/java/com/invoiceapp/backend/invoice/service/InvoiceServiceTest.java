package com.invoiceapp.backend.invoice.service;

import com.invoiceapp.backend.auth.domain.User;
import com.invoiceapp.backend.client.domain.Client;
import com.invoiceapp.backend.client.domain.ClientRepository;
import com.invoiceapp.backend.invoice.domain.*;
import com.invoiceapp.backend.invoice.event.InvoiceReadyForDeliveryEvent;
import com.invoiceapp.backend.shared.audit.AuditAction;
import com.invoiceapp.backend.shared.audit.AuditService;
import com.invoiceapp.backend.shared.outbox.OutboxService;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
    private CurrentUserResolver currentUserResolver;
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private InvoiceMetrics invoiceMetrics;
    @Mock
    private AuditService auditService;
    @Mock
    private OutboxService outboxService;

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

        lenient().when(currentUserResolver.resolveUser()).thenReturn(testUser);

        ReflectionTestUtils.setField(invoiceService, "deliveryTopic", "invoice.delivery");
    }

    private Invoice buildInvoice(InvoiceStatus status, Long version) {
        Invoice invoice = Invoice.builder()
                .id(UUID.randomUUID())
                .invoiceNumber("INV-2026-00001")
                .client(testClient)
                .createdBy(testUser)
                .status(status)
                .issueDate(LocalDate.now())
                .dueDate(LocalDate.now().plusDays(30))
                .taxRate(new BigDecimal("0.20"))
                .notes("Original Notes")
                .createdAt(Instant.now())
                .lineItems(new ArrayList<>())
                .version(version)
                .build();

        LineItem li = LineItem.builder()
                .invoice(invoice)
                .description("Consulting")
                .quantity(new BigDecimal("10"))
                .unitPrice(new BigDecimal("276.00"))
                .discountPct(BigDecimal.ZERO)
                .position(1)
                .build();
        li.computeLineTotal();
        invoice.getLineItems().add(li);
        invoice.recalculateTotals();
        return invoice;
    }

    @Nested
    @DisplayName("invoice state transitions")
    class StateTransitions {

        @Test
        @DisplayName("should transition from DRAFT to SENT successfully and log workflow audit event")
        void should_transition_draft_to_sent() {
            Invoice invoice = buildInvoice(InvoiceStatus.DRAFT, 2L);
            when(invoiceRepository.findByIdAndCreatedById(invoice.getId(), userId)).thenReturn(Optional.of(invoice));
            when(invoiceRepository.computeOutstandingBalance()).thenReturn(BigDecimal.ZERO);

            InvoiceService.InvoiceResponse response = invoiceService.send(invoice.getId(), 2L);

            assertThat(response.status()).isEqualTo(InvoiceStatus.SENT);

            verify(auditService, times(1)).log(
                    eq("INVOICE"),
                    eq(invoice.getId()),
                    eq(AuditAction.INVOICE_SENT),
                    eq(Map.of("status", "DRAFT")),
                    eq(Map.of("status", "SENT")),
                    eq(userId)
            );
        }

        @Test
        @DisplayName("should throw OptimisticLockingFailureException on out-of-sync transition push and skip audit steps")
        void should_throw_optimistic_lock_on_transition() {
            Invoice invoice = buildInvoice(InvoiceStatus.DRAFT, 2L);
            when(invoiceRepository.findByIdAndCreatedById(invoice.getId(), userId)).thenReturn(Optional.of(invoice));

            assertThatThrownBy(() -> invoiceService.send(invoice.getId(), 1L))
                    .isInstanceOf(ObjectOptimisticLockingFailureException.class);

            verifyNoInteractions(auditService);
        }

        @Test
        @DisplayName("should throw when transitioning PAID invoice to any status and skip audit")
        void should_throw_when_transitioning_paid_invoice() {
            Invoice invoice = buildInvoice(InvoiceStatus.PAID, 0L);
            when(invoiceRepository.findByIdAndCreatedById(invoice.getId(), userId)).thenReturn(Optional.of(invoice));

            assertThatThrownBy(() -> invoiceService.send(invoice.getId(), 0L))
                    .isInstanceOf(InvoiceAppException.class)
                    .hasMessageContaining("Cannot transition invoice from PAID");

            verifyNoInteractions(auditService);
        }

        @Test
        @DisplayName("should throw when transitioning CANCELLED invoice and skip audit")
        void should_throw_when_transitioning_cancelled_invoice() {
            Invoice invoice = buildInvoice(InvoiceStatus.CANCELLED, 0L);
            when(invoiceRepository.findByIdAndCreatedById(invoice.getId(), userId)).thenReturn(Optional.of(invoice));

            assertThatThrownBy(() -> invoiceService.send(invoice.getId(), 0L))
                    .isInstanceOf(InvoiceAppException.class)
                    .hasMessageContaining("Cannot transition invoice from CANCELLED");

            verifyNoInteractions(auditService);
        }

        @Test
        @DisplayName("should throw 404 when invoice not found")
        void should_throw_404_when_invoice_not_found() {
            UUID randomId = UUID.randomUUID();
            when(invoiceRepository.findByIdAndCreatedById(randomId, userId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> invoiceService.send(randomId, 0L))
                    .isInstanceOf(InvoiceAppException.class)
                    .hasMessageContaining("Invoice not found");

            verifyNoInteractions(auditService);
        }

        @Test
        @DisplayName("should allow OVERDUE invoice to be marked PAID and log transition audit record")
        void should_allow_overdue_invoice_to_be_marked_paid() {
            Invoice invoice = buildInvoice(InvoiceStatus.OVERDUE, 0L);

            when(invoiceRepository.findByIdAndCreatedById(invoice.getId(), userId)).thenReturn(Optional.of(invoice));
            when(paymentRepository.sumAmountByInvoiceId(invoice.getId())).thenReturn(new BigDecimal("3312.0000"));
            when(invoiceRepository.computeOutstandingBalance()).thenReturn(BigDecimal.ZERO);

            InvoiceService.InvoiceResponse response = invoiceService.markPaid(invoice.getId(), 0L);

            assertThat(response.status()).isEqualTo(InvoiceStatus.PAID);

            verify(auditService, times(1)).log(
                    eq("INVOICE"),
                    eq(invoice.getId()),
                    eq(AuditAction.INVOICE_PAID),
                    eq(Map.of("status", "OVERDUE")),
                    eq(Map.of("status", "PAID")),
                    eq(userId)
            );
        }

        @Test
        @DisplayName("should record compensating payment when manually marking paid with balance remaining")
        void should_record_compensating_payment_when_marking_paid_with_remaining_balance() {
            Invoice invoice = buildInvoice(InvoiceStatus.SENT, 0L);

            when(invoiceRepository.findByIdAndCreatedById(invoice.getId(), userId)).thenReturn(Optional.of(invoice));
            when(paymentRepository.sumAmountByInvoiceId(invoice.getId())).thenReturn(BigDecimal.ZERO);
            when(invoiceRepository.computeOutstandingBalance()).thenReturn(BigDecimal.ZERO);

            InvoiceService.InvoiceResponse response = invoiceService.markPaid(invoice.getId(), 0L);

            assertThat(response.status()).isEqualTo(InvoiceStatus.PAID);

            verify(paymentRepository, times(1)).save(argThat(payment ->
                    payment.getAmount().compareTo(new BigDecimal("3312.0000")) == 0
                            && "MANUAL_MARK_PAID".equals(payment.getMethod())
            ));

            verify(auditService, times(1)).log(
                    eq("INVOICE"),
                    eq(invoice.getId()),
                    eq(AuditAction.INVOICE_PAID),
                    eq(Map.of("status", "SENT")),
                    eq(Map.of("status", "PAID")),
                    eq(userId)
            );
        }

        @Test
        @DisplayName("should not record compensating payment when balance is already zero")
        void should_not_record_compensating_payment_when_already_fully_paid() {
            Invoice invoice = buildInvoice(InvoiceStatus.SENT, 0L);

            when(invoiceRepository.findByIdAndCreatedById(invoice.getId(), userId)).thenReturn(Optional.of(invoice));
            when(paymentRepository.sumAmountByInvoiceId(invoice.getId())).thenReturn(new BigDecimal("3312.0000"));
            when(invoiceRepository.computeOutstandingBalance()).thenReturn(BigDecimal.ZERO);

            InvoiceService.InvoiceResponse response = invoiceService.markPaid(invoice.getId(), 0L);

            assertThat(response.status()).isEqualTo(InvoiceStatus.PAID);

            verify(paymentRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("invoice total calculations")
    class Calculations {

        @Test
        @DisplayName("should calculate totals correctly with tax and discount and save initial state snapshot")
        void should_calculate_totals_correctly() {
            UUID invoiceId = UUID.randomUUID();
            Instant now = Instant.now();
            LocalDate issueDate = LocalDate.now();
            LocalDate dueDate = LocalDate.now().plusDays(30);

            when(clientRepository.findByIdAndOwnerId(clientId, userId)).thenReturn(Optional.of(testClient));
            when(invoiceRepository.nextInvoiceSequence()).thenReturn(1L);
            when(invoiceRepository.save(any())).thenAnswer(inv -> {
                Invoice i = inv.getArgument(0);
                i.setId(invoiceId);
                i.setCreatedAt(now);
                i.setStatus(InvoiceStatus.DRAFT);
                return i;
            });

            InvoiceService.InvoiceRequest request = new InvoiceService.InvoiceRequest(
                    clientId,
                    issueDate,
                    dueDate,
                    new BigDecimal("0.20"),
                    "Test invoice",
                    List.of(
                            new InvoiceService.LineItemRequest("Frontend development", new BigDecimal("10"), new BigDecimal("150.00"), new BigDecimal("0.00"), 1),
                            new InvoiceService.LineItemRequest("Backend development", new BigDecimal("8"), new BigDecimal("175.00"), new BigDecimal("0.10"), 2)
                    )
            );

            InvoiceService.InvoiceResponse response = invoiceService.create(request);

            assertThat(response.subtotal()).isEqualByComparingTo(new BigDecimal("2760.0000"));
            assertThat(response.taxAmount()).isEqualByComparingTo(new BigDecimal("552.0000"));
            assertThat(response.total()).isEqualByComparingTo(new BigDecimal("3312.0000"));

            verify(auditService, times(1)).log(
                    eq("INVOICE"),
                    eq(invoiceId),
                    eq(AuditAction.INVOICE_CREATED),
                    isNull(),
                    argThat(state -> {
                        if (!(state instanceof Map<?, ?> stateMap)) return false;
                        var lines = stateMap.get("lineItems");
                        return "DRAFT".equals(stateMap.get("status")) &&
                                "2760.0000".equals(stateMap.get("subtotal")) &&
                                "3312.0000".equals(stateMap.get("total")) &&
                                lines instanceof List<?> list && list.size() == 2;
                    }),
                    eq(userId)
            );
        }

        @Test
        @DisplayName("should reject invoice when due date is before issue date")
        void should_reject_invoice_with_invalid_dates() {
            when(clientRepository.findByIdAndOwnerId(clientId, userId)).thenReturn(Optional.of(testClient));

            InvoiceService.InvoiceRequest request = new InvoiceService.InvoiceRequest(
                    clientId,
                    LocalDate.of(2026, 2, 15),
                    LocalDate.of(2026, 1, 15),
                    BigDecimal.ZERO,
                    null,
                    List.of(new InvoiceService.LineItemRequest("Test", BigDecimal.ONE, BigDecimal.TEN, BigDecimal.ZERO, 1))
            );

            assertThatThrownBy(() -> invoiceService.create(request))
                    .isInstanceOf(InvoiceAppException.class)
                    .hasMessageContaining("Due date cannot be before issue date");

            verifyNoInteractions(auditService);
        }
    }

    @Nested
    @DisplayName("invoice retrieval")
    class Retrieval {

        @Test
        @DisplayName("should find all invoices with filters without writing an audit record")
        void should_find_all_invoices() {
            Invoice invoice = buildInvoice(InvoiceStatus.DRAFT, 0L);
            Page<Invoice> page = new PageImpl<>(List.of(invoice));

            when(invoiceRepository.findAllByFilters(eq(userId), any(), any(), any())).thenReturn(page);

            var result = invoiceService.findAll(InvoiceStatus.DRAFT, clientId, Pageable.unpaged());

            assertThat(result.getContent()).hasSize(1);
            verifyNoInteractions(auditService);
        }

        @Test
        @DisplayName("should find invoice by id without writing an audit record")
        void should_find_by_id() {
            Invoice invoice = buildInvoice(InvoiceStatus.DRAFT, 0L);
            when(invoiceRepository.findByIdAndCreatedById(invoice.getId(), userId)).thenReturn(Optional.of(invoice));

            var result = invoiceService.findById(invoice.getId());

            assertThat(result.id()).isEqualTo(invoice.getId());
            verifyNoInteractions(auditService);
        }
    }

    @Test
    @DisplayName("should cancel invoice successfully and log transition event")
    void should_cancel_invoice() {
        Invoice invoice = buildInvoice(InvoiceStatus.DRAFT, 0L);
        when(invoiceRepository.findByIdAndCreatedById(invoice.getId(), userId)).thenReturn(Optional.of(invoice));
        when(invoiceRepository.computeOutstandingBalance()).thenReturn(BigDecimal.ZERO);

        var response = invoiceService.cancel(invoice.getId(), 0L);

        assertThat(response.status()).isEqualTo(InvoiceStatus.CANCELLED);
        verify(invoiceMetrics).recordStatusTransition(InvoiceStatus.CANCELLED);

        verify(auditService, times(1)).log(
                eq("INVOICE"),
                eq(invoice.getId()),
                eq(AuditAction.INVOICE_CANCELLED),
                eq(Map.of("status", "DRAFT")),
                eq(Map.of("status", "CANCELLED")),
                eq(userId)
        );
    }

    @Nested
    @DisplayName("invoice modifications")
    class Modifications {

        @Test
        @DisplayName("should update invoice metadata and log deep structured snapshots of object mutations")
        void should_update_invoice() {
            Invoice invoice = buildInvoice(InvoiceStatus.DRAFT, 4L);

            when(invoiceRepository.findByIdAndCreatedById(invoice.getId(), userId)).thenReturn(Optional.of(invoice));

            LocalDate newIssue = LocalDate.of(2026, 5, 19);
            LocalDate newDue = LocalDate.of(2026, 6, 19);
            BigDecimal newTax = new BigDecimal("0.10");
            String newNotes = "Updated billing info";
            List<InvoiceService.LineItemRequest> newItems = List.of(
                    new InvoiceService.LineItemRequest("New Item", BigDecimal.ONE, BigDecimal.TEN, BigDecimal.ZERO, 1)
            );

            var response = invoiceService.update(invoice.getId(), 4L, newIssue, newDue, newTax, newNotes, newItems);

            assertThat(response.issueDate()).isEqualTo(newIssue);
            assertThat(response.dueDate()).isEqualTo(newDue);
            assertThat(response.taxRate()).isEqualByComparingTo(newTax);
            assertThat(response.notes()).isEqualTo(newNotes);
            assertThat(response.lineItems()).hasSize(1);

            verify(auditService, times(1)).log(
                    eq("INVOICE"),
                    eq(invoice.getId()),
                    eq(AuditAction.INVOICE_UPDATED),
                    argThat(oldState -> oldState instanceof Map<?, ?> m && "Original Notes".equals(m.get("notes"))),
                    argThat(newState -> newState instanceof Map<?, ?> m && "Updated billing info".equals(m.get("notes"))),
                    eq(userId)
            );
        }

        @Test
        @DisplayName("should throw OptimisticLockingFailureException on stale invoice edit requests")
        void should_throw_optimistic_lock_on_invoice_edit() {
            Invoice invoice = buildInvoice(InvoiceStatus.DRAFT, 4L);
            when(invoiceRepository.findByIdAndCreatedById(invoice.getId(), userId)).thenReturn(Optional.of(invoice));

            List<InvoiceService.LineItemRequest> validItems = List.of(
                    new InvoiceService.LineItemRequest("Test Item", BigDecimal.ONE, BigDecimal.TEN, BigDecimal.ZERO, 1)
            );

            assertThatThrownBy(() -> invoiceService.update(
                    invoice.getId(), 3L, LocalDate.now(), LocalDate.now().plusDays(10), BigDecimal.ZERO, null, validItems
            )).isInstanceOf(ObjectOptimisticLockingFailureException.class);

            verifyNoInteractions(auditService);
        }

        @Test
        @DisplayName("should throw when updating non-DRAFT invoice")
        void should_throw_when_updating_sent_invoice() {
            Invoice invoice = buildInvoice(InvoiceStatus.SENT, 1L);
            when(invoiceRepository.findByIdAndCreatedById(invoice.getId(), userId)).thenReturn(Optional.of(invoice));

            List<InvoiceService.LineItemRequest> validItems = List.of(
                    new InvoiceService.LineItemRequest("Test Item", BigDecimal.ONE, BigDecimal.TEN, BigDecimal.ZERO, 1)
            );

            assertThatThrownBy(() -> invoiceService.update(
                    invoice.getId(), 1L, LocalDate.now(), LocalDate.now().plusDays(10), BigDecimal.ZERO, null, validItems
            )).isInstanceOf(InvoiceAppException.class);

            verifyNoInteractions(auditService);
        }

        @Test
        @DisplayName("should completely skip optimistic lock validation check when version parameter is null")
        void should_skip_optimistic_lock_check_when_version_is_null() {
            Invoice invoice = buildInvoice(InvoiceStatus.DRAFT, 4L);
            when(invoiceRepository.findByIdAndCreatedById(invoice.getId(), userId)).thenReturn(Optional.of(invoice));

            LocalDate newIssue = LocalDate.now();
            LocalDate newDue = LocalDate.now().plusDays(30);
            List<InvoiceService.LineItemRequest> validItems = List.of(
                    new InvoiceService.LineItemRequest("Test Item", BigDecimal.ONE, BigDecimal.TEN, BigDecimal.ZERO, 1)
            );

            var response = invoiceService.update(
                    invoice.getId(), null, newIssue, newDue, BigDecimal.ZERO, "No lock parameter provided", validItems
            );

            assertThat(response).isNotNull();
            assertThat(response.notes()).isEqualTo("No lock parameter provided");
        }

        @Test
        @DisplayName("should throw Unprocessable Content Exception when line items collection is null")
        void should_throw_when_line_items_null_on_update() {
            Invoice invoice = buildInvoice(InvoiceStatus.DRAFT, 1L);
            when(invoiceRepository.findByIdAndCreatedById(invoice.getId(), userId)).thenReturn(Optional.of(invoice));

            assertThatThrownBy(() -> invoiceService.update(
                    invoice.getId(), 1L, LocalDate.now(), LocalDate.now().plusDays(10), BigDecimal.ZERO, "No items", null
            )).isInstanceOf(InvoiceAppException.class)
                    .hasMessageContaining("An invoice must contain at least one line item");

            verifyNoInteractions(auditService);
        }

        @Test
        @DisplayName("should throw Unprocessable Content Exception when line items collection is empty")
        void should_throw_when_line_items_empty_on_update() {
            Invoice invoice = buildInvoice(InvoiceStatus.DRAFT, 1L);
            when(invoiceRepository.findByIdAndCreatedById(invoice.getId(), userId)).thenReturn(Optional.of(invoice));

            assertThatThrownBy(() -> invoiceService.update(
                    invoice.getId(), 1L, LocalDate.now(), LocalDate.now().plusDays(10), BigDecimal.ZERO, "Empty items", List.of()
            ))
                    .isInstanceOf(InvoiceAppException.class)
                    .hasMessageContaining("An invoice must contain at least one line item");

            verifyNoInteractions(auditService);
        }

        @Test
        @DisplayName("should throw Unprocessable Content Exception when update request due date is before issue date")
        void should_throw_when_due_date_before_issue_date_on_update() {
            Invoice invoice = buildInvoice(InvoiceStatus.DRAFT, 1L);
            when(invoiceRepository.findByIdAndCreatedById(invoice.getId(), userId)).thenReturn(Optional.of(invoice));

            LocalDate invalidIssueDate = LocalDate.of(2026, 5, 20);
            LocalDate invalidDueDate = LocalDate.of(2026, 5, 10);

            List<InvoiceService.LineItemRequest> validItems = List.of(
                    new InvoiceService.LineItemRequest("Test Item", BigDecimal.ONE, BigDecimal.TEN, BigDecimal.ZERO, 1)
            );

            assertThatThrownBy(() -> invoiceService.update(
                    invoice.getId(), 1L, invalidIssueDate, invalidDueDate, BigDecimal.ZERO, "Invalid Dates", validItems
            ))
                    .isInstanceOf(InvoiceAppException.class)
                    .hasMessageContaining("Due date cannot be before issue date");

            verifyNoInteractions(auditService);
        }
    }

    @Nested
    @DisplayName("system overdue detection")
    class OverdueDetection {

        @Test
        @DisplayName("marks SENT invoices past due as OVERDUE, audits as system, publishes one batch")
        void marks_overdue() {
            Invoice inv1 = buildInvoice(InvoiceStatus.SENT, 0L);
            Invoice inv2 = buildInvoice(InvoiceStatus.SENT, 0L);
            when(invoiceRepository.findAllOverdue(any(LocalDate.class))).thenReturn(List.of(inv1, inv2));

            int count = invoiceService.markOverdueInvoices();

            assertThat(count).isEqualTo(2);
            assertThat(inv1.getStatus()).isEqualTo(InvoiceStatus.OVERDUE);
            assertThat(inv2.getStatus()).isEqualTo(InvoiceStatus.OVERDUE);

            verify(auditService, times(2)).log(
                    eq("INVOICE"), any(UUID.class), eq(AuditAction.INVOICE_OVERDUE),
                    any(), any(), eq(new UUID(0L, 0L)));

            verify(outboxService, times(1))
                    .publishAll(eq("INVOICE"), eq("InvoiceStatusChanged"), anyList());
        }

        @Test
        @DisplayName("does nothing when no invoices are overdue")
        void no_overdue() {
            when(invoiceRepository.findAllOverdue(any(LocalDate.class))).thenReturn(List.of());

            int count = invoiceService.markOverdueInvoices();

            assertThat(count).isZero();
            verifyNoInteractions(outboxService);
            verifyNoInteractions(auditService);
        }
    }

    @Test
    @DisplayName("emits a delivery event when an invoice is sent")
    void emits_delivery_event_on_send() {
        Invoice invoice = buildInvoice(InvoiceStatus.DRAFT, 1L);
        when(invoiceRepository.findByIdAndCreatedById(invoice.getId(), userId)).thenReturn(Optional.of(invoice));

        invoiceService.send(invoice.getId(), 1L);

        verify(outboxService).publishTo(
                eq("invoice.delivery"), eq("INVOICE"), eq(invoice.getId()),
                eq("InvoiceReadyForDelivery"), any(InvoiceReadyForDeliveryEvent.class));
    }

    @Test
    @DisplayName("skips the delivery event when the client has no email")
    void skips_delivery_event_without_email() {
        Invoice invoice = buildInvoice(InvoiceStatus.DRAFT, 1L);
        invoice.getClient().setEmail(null);
        when(invoiceRepository.findByIdAndCreatedById(invoice.getId(), userId)).thenReturn(Optional.of(invoice));

        invoiceService.send(invoice.getId(), 1L);

        verify(outboxService, never()).publishTo(any(), any(), any(), any(), any());
        // the status event still fires
        verify(outboxService).publish(any(), any(), any(), any());
    }
}