package com.invoiceapp.backend.invoice.domain;

import com.invoiceapp.backend.auth.domain.Role;
import com.invoiceapp.backend.auth.domain.User;
import com.invoiceapp.backend.auth.domain.UserRepository;
import com.invoiceapp.backend.client.domain.Client;
import com.invoiceapp.backend.client.domain.ClientRepository;
import com.invoiceapp.backend.config.PostgresTestContainer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
@DisplayName("InvoiceRepository")
class InvoiceRepositoryTest extends PostgresTestContainer {

    @Autowired private InvoiceRepository invoiceRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ClientRepository clientRepository;
    @Autowired private PaymentRepository paymentRepository;

    private User savedUser;
    private Client savedClient;

    @BeforeEach
    void setUp() {
        savedUser = userRepository.save(User.builder()
                .email("repo-test@example.com")
                .password("hashed")
                .role(Role.USER)
                .build());

        savedClient = clientRepository.save(Client.builder()
                .owner(savedUser)
                .name("Test Client")
                .build());
    }

    @Test
    @DisplayName("should find invoices by user ID with correct ownership scoping")
    void should_find_invoices_by_user_id() {
        User otherUser = userRepository.save(User.builder()
                .email("other@example.com")
                .password("hashed")
                .role(Role.USER)
                .build());

        Client otherClient = clientRepository.save(Client.builder()
                .owner(otherUser)
                .name("Other Client")
                .build());

        Invoice myInvoice = saveInvoice(savedClient, savedUser, InvoiceStatus.SENT);
        saveInvoice(otherClient, otherUser, InvoiceStatus.SENT);

        Optional<Invoice> found = invoiceRepository.findByIdAndCreatedById(myInvoice.getId(), savedUser.getId());
        Optional<Invoice> notFound = invoiceRepository.findByIdAndCreatedById(myInvoice.getId(), otherUser.getId());

        assertThat(found).isPresent();
        assertThat(notFound).isEmpty();
    }

    @Test
    @DisplayName("should find overdue invoices — only SENT with past due date")
    void should_find_overdue_invoices() {
        Invoice overdue = Invoice.builder()
                .invoiceNumber("INV-TEST-001")
                .client(savedClient)
                .createdBy(savedUser)
                .status(InvoiceStatus.SENT)
                .issueDate(LocalDate.now().minusDays(60))
                .dueDate(LocalDate.now().minusDays(30))
                .subtotal(BigDecimal.TEN)
                .taxRate(BigDecimal.ZERO)
                .taxAmount(BigDecimal.ZERO)
                .total(BigDecimal.TEN)
                .build();
        invoiceRepository.save(overdue);

        Invoice paid = Invoice.builder()
                .invoiceNumber("INV-TEST-002")
                .client(savedClient)
                .createdBy(savedUser)
                .status(InvoiceStatus.PAID)
                .issueDate(LocalDate.now().minusDays(60))
                .dueDate(LocalDate.now().minusDays(30))
                .subtotal(BigDecimal.TEN)
                .taxRate(BigDecimal.ZERO)
                .taxAmount(BigDecimal.ZERO)
                .total(BigDecimal.TEN)
                .build();
        invoiceRepository.save(paid);

        Invoice notDue = saveInvoice(savedClient, savedUser, InvoiceStatus.SENT);

        List<Invoice> overdueInvoices = invoiceRepository.findAllOverdue(LocalDate.now());

        assertThat(overdueInvoices).hasSize(1);
        assertThat(overdueInvoices.getFirst().getInvoiceNumber()).isEqualTo("INV-TEST-001");
    }

    @Test
    @DisplayName("should compute outstanding balance correctly")
    void should_compute_outstanding_balance() {
        Invoice invoice1 = saveInvoiceWithTotal(new BigDecimal("2000.0000"), InvoiceStatus.SENT);
        Invoice invoice2 = saveInvoiceWithTotal(new BigDecimal("1000.0000"), InvoiceStatus.SENT);

        paymentRepository.save(Payment.builder()
                .invoice(invoice1)
                .amount(new BigDecimal("500.0000"))
                .build());

        saveInvoiceWithTotal(new BigDecimal("999.0000"), InvoiceStatus.PAID);

        BigDecimal outstanding = invoiceRepository.computeOutstandingBalance();

        assertThat(outstanding).isEqualByComparingTo(new BigDecimal("2500"));
    }

    private Invoice saveInvoice(Client client, User user, InvoiceStatus status) {
        return invoiceRepository.save(Invoice.builder()
                .invoiceNumber("INV-TEST-" + UUID.randomUUID().toString().substring(0, 8))
                .client(client)
                .createdBy(user)
                .status(status)
                .issueDate(LocalDate.now())
                .dueDate(LocalDate.now().plusDays(30))
                .subtotal(new BigDecimal("100.0000"))
                .taxRate(BigDecimal.ZERO)
                .taxAmount(BigDecimal.ZERO)
                .total(new BigDecimal("100.0000"))
                .build());
    }

    private Invoice saveInvoiceWithTotal(BigDecimal total, InvoiceStatus status) {
        return invoiceRepository.save(Invoice.builder()
                .invoiceNumber("INV-TEST-" + UUID.randomUUID().toString().substring(0, 8))
                .client(savedClient)
                .createdBy(savedUser)
                .status(status)
                .issueDate(LocalDate.now())
                .dueDate(LocalDate.now().plusDays(30))
                .subtotal(total)
                .taxRate(BigDecimal.ZERO)
                .taxAmount(BigDecimal.ZERO)
                .total(total)
                .build());
    }
}