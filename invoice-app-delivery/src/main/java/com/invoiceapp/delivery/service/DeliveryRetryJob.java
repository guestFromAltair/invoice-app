package com.invoiceapp.delivery.service;

import com.invoiceapp.delivery.domain.DeliveryAttempt;
import com.invoiceapp.delivery.domain.DeliveryAttemptRepository;
import com.invoiceapp.delivery.event.InvoiceReadyForDeliveryEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class DeliveryRetryJob {
    private final DeliveryAttemptRepository repository;
    private final DeliveryService deliveryService;
    private final JsonMapper jsonMapper;

    @Value("${application.delivery.retry-batch-size}")
    private int batchSize;

    @Scheduled(fixedDelayString = "${application.delivery.retry-poll-ms}")
    @Transactional
    public void retryFailedDeliveries() {
        List<DeliveryAttempt> due = repository.findDueForRetry(batchSize);
        log.debug("Retry poll: {} attempt(s) due", due.size());

        if (due.isEmpty()) {
            return;
        }

        for (DeliveryAttempt attempt : due) {
            try {
                InvoiceReadyForDeliveryEvent event = jsonMapper.readValue(attempt.getPayload(), InvoiceReadyForDeliveryEvent.class);
                deliveryService.attemptDelivery(attempt, event);
            } catch (Exception e) {
                log.error("Could not retry delivery {}", attempt.getInvoiceNumber(), e);
            }
        }

        repository.saveAll(due);
        log.debug("Retried {} delivery attempt(s)", due.size());
    }
}