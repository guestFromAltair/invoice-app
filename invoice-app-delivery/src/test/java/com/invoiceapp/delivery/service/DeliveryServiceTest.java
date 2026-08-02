package com.invoiceapp.delivery.service;

import com.invoiceapp.delivery.InvoicePdfRenderer;
import com.invoiceapp.delivery.domain.DeliveryAttempt;
import com.invoiceapp.delivery.domain.DeliveryStatus;
import com.invoiceapp.delivery.email.InvoiceEmailSender;
import com.invoiceapp.delivery.event.InvoiceReadyForDeliveryEvent;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("DeliveryService")
class DeliveryServiceTest {
    @Mock private InvoicePdfRenderer renderer;
    @Mock private InvoiceEmailSender emailSender;

    private DeliveryService service;

    @BeforeEach
    void setUp() {
        service = new DeliveryService(renderer, emailSender);
        ReflectionTestUtils.setField(service, "maxAttempts", 3);
        ReflectionTestUtils.setField(service, "retryBaseSeconds", 60L);
        ReflectionTestUtils.setField(service, "retryMaxSeconds", 21600L);
    }

    @Test
    @DisplayName("marks SENT on success")
    void sends() throws Exception {
        when(renderer.render(any())).thenReturn(new byte[]{1, 2, 3});
        DeliveryAttempt a = attempt("billing@acme.com", 0);

        service.attemptDelivery(a, event());

        assertThat(a.getStatus()).isEqualTo(DeliveryStatus.SENT);
        assertThat(a.getNextAttemptAt()).isNull();
        verify(emailSender).send(eq("billing@acme.com"), any(), any(), any());
    }

    @Test
    @DisplayName("schedules a retry on a transient failure")
    void retries() throws Exception {
        when(renderer.render(any())).thenReturn(new byte[]{1});
        doThrow(new RuntimeException("smtp down")).when(emailSender).send(any(), any(), any(), any());
        DeliveryAttempt a = attempt("billing@acme.com", 0);

        service.attemptDelivery(a, event());

        assertThat(a.getStatus()).isEqualTo(DeliveryStatus.FAILED);
        assertThat(a.getAttempts()).isEqualTo(1);
        assertThat(a.getNextAttemptAt()).isAfter(Instant.now());
    }

    @Test
    @DisplayName("abandons after the attempt limit")
    void abandons_after_limit() throws Exception {
        when(renderer.render(any())).thenReturn(new byte[]{1});
        doThrow(new RuntimeException("still down")).when(emailSender).send(any(), any(), any(), any());
        DeliveryAttempt a = attempt("billing@acme.com", 2);

        service.attemptDelivery(a, event());

        assertThat(a.getStatus()).isEqualTo(DeliveryStatus.ABANDONED);
        assertThat(a.getNextAttemptAt()).isNull();
    }

    @Test
    @DisplayName("abandons immediately on a malformed address")
    void abandons_bad_address() throws Exception {
        DeliveryAttempt a = attempt("not-an-email", 0);

        service.attemptDelivery(a, event());

        assertThat(a.getStatus()).isEqualTo(DeliveryStatus.ABANDONED);
        verifyNoInteractions(emailSender);
    }

    private DeliveryAttempt attempt(String recipient, int attempts) {
        return DeliveryAttempt.builder()
                .invoiceId(UUID.randomUUID()).invoiceNumber("INV-2026-00001")
                .ownerId(UUID.randomUUID()).recipient(recipient)
                .status(DeliveryStatus.PENDING).attempts(attempts)
                .build();
    }

    private InvoiceReadyForDeliveryEvent event() {
        return new InvoiceReadyForDeliveryEvent(
                UUID.randomUUID(), "INV-2026-00001", UUID.randomUUID(), "SENT",
                new InvoiceReadyForDeliveryEvent.Recipient("Acme", "billing@acme.com", null, null),
                LocalDate.now(), LocalDate.now().plusDays(30),
                BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.TEN,
                null, List.of(), Instant.now());
    }
}