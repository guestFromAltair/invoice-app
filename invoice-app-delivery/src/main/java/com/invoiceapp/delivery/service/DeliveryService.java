package com.invoiceapp.delivery.service;

import com.invoiceapp.delivery.domain.DeliveryAttempt;
import com.invoiceapp.delivery.domain.DeliveryStatus;
import com.invoiceapp.delivery.email.InvoiceEmailSender;
import com.invoiceapp.delivery.event.InvoiceReadyForDeliveryEvent;
import com.invoiceapp.delivery.InvoicePdfRenderer;
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

    public void attemptDelivery(DeliveryAttempt attempt, InvoiceReadyForDeliveryEvent event) {
        attempt.setAttempts(attempt.getAttempts() + 1);

        if (!InvoiceEmailSender.isValidAddress(attempt.getRecipient())) {
            abandon(attempt, "Invalid recipient address");
            return;
        }

        try {
            byte[] pdf = renderer.render(event);
            emailSender.send(attempt.getRecipient(), event.invoiceNumber(), event.recipient().name(), pdf);

            attempt.setStatus(DeliveryStatus.SENT);
            attempt.setNextAttemptAt(null);
            attempt.setLastError(null);
            log.info("Sent invoice {} to {}", event.invoiceNumber(), attempt.getRecipient());

        } catch (Exception e) {
            if (attempt.getAttempts() >= maxAttempts) {
                abandon(attempt, "Gave up after " + attempt.getAttempts() + " attempts: " + e.getMessage());
            } else {
                attempt.setStatus(DeliveryStatus.FAILED);
                attempt.setLastError(e.getMessage());
                attempt.setNextAttemptAt(Instant.now().plusSeconds(backoffSeconds(attempt.getAttempts())));
                log.warn("Delivery of {} failed (attempt {}), retrying at {}",
                        event.invoiceNumber(), attempt.getAttempts(), attempt.getNextAttemptAt());
            }
        }
    }

    private void abandon(DeliveryAttempt attempt, String reason) {
        attempt.setStatus(DeliveryStatus.ABANDONED);
        attempt.setLastError(reason);
        attempt.setNextAttemptAt(null);
        log.error("Abandoned delivery of {}: {}", attempt.getInvoiceNumber(), reason);
    }

    private long backoffSeconds(int attempts) {
        return Math.min(retryMaxSeconds, retryBaseSeconds * (1L << (attempts - 1)));
    }
}