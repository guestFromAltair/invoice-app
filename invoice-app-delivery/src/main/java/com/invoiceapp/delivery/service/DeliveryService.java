package com.invoiceapp.delivery.service;

import com.invoiceapp.delivery.InvoicePdfRenderer;
import com.invoiceapp.delivery.domain.DeliveryAttempt;
import com.invoiceapp.delivery.email.InvoiceEmailSender;
import com.invoiceapp.delivery.event.InvoiceReadyForDeliveryEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@RequiredArgsConstructor
@Slf4j
public class DeliveryService {
    private final InvoicePdfRenderer renderer;
    private final InvoiceEmailSender emailSender;

    @Value("${application.delivery.max-attempts}")
    private int maxAttempts;

    @Value("${application.delivery.retry-base-seconds}")
    private long retryBaseSeconds;

    @Value("${application.delivery.retry-max-seconds}")
    private long retryMaxSeconds;

    public DeliveryOutcome attemptDelivery(DeliveryAttempt attempt, InvoiceReadyForDeliveryEvent event) {
        int attemptNumber = attempt.getAttempts() + 1;

        if (!InvoiceEmailSender.isValidAddress(attempt.getRecipient())) {
            log.error("Abandoning delivery of {}: invalid address '{}'",
                    attempt.getInvoiceNumber(), attempt.getRecipient());
            return DeliveryOutcome.abandoned(attemptNumber, "Invalid recipient address");
        }

        try {
            byte[] pdf = renderer.render(event);
            emailSender.send(attempt.getRecipient(), event.invoiceNumber(), event.recipient().name(), pdf);

            log.info("Sent invoice {} to {}", event.invoiceNumber(), attempt.getRecipient());
            return DeliveryOutcome.sent(attemptNumber);
        } catch (Exception e) {
            if (attemptNumber >= maxAttempts) {
                log.error("Abandoning delivery of {} after {} attempts", attempt.getInvoiceNumber(), attemptNumber, e);
                return DeliveryOutcome.abandoned(attemptNumber,
                        "Gave up after " + attemptNumber + " attempts: " + e.getMessage());
            }

            Instant nextAttempt = Instant.now().plusSeconds(backoffSeconds(attemptNumber));
            log.warn("Delivery of {} failed (attempt {}), retrying at {}",
                    attempt.getInvoiceNumber(), attemptNumber, nextAttempt);
            return DeliveryOutcome.retry(attemptNumber, e.getMessage(), nextAttempt);
        }
    }

    private long backoffSeconds(int attemptNumber) {
        return Math.min(retryMaxSeconds, retryBaseSeconds * (1L << (attemptNumber - 1)));
    }
}