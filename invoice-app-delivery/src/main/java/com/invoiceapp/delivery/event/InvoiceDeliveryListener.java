package com.invoiceapp.delivery.event;

import com.invoiceapp.delivery.domain.DeliveryAttempt;
import com.invoiceapp.delivery.service.DeliveryOutcome;
import com.invoiceapp.delivery.service.DeliveryService;
import com.invoiceapp.delivery.service.DeliveryStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class InvoiceDeliveryListener {

    private final DeliveryStore deliveryStore;
    private final DeliveryService deliveryService;
    private final JsonMapper jsonMapper;

    @KafkaListener(topics = "${application.kafka.topic.invoice-delivery}")
    public void onInvoiceReadyForDelivery(ConsumerRecord<String, String> record) {
        InvoiceReadyForDeliveryEvent event =
                jsonMapper.readValue(record.value(), InvoiceReadyForDeliveryEvent.class);

        Optional<DeliveryAttempt> claimed = deliveryStore.claimNew(event, record.value());
        if (claimed.isEmpty()) {
            log.info("Invoice {} already has a delivery record, skipping", event.invoiceNumber());
            return;
        }

        DeliveryAttempt attempt = claimed.get();
        DeliveryOutcome outcome = deliveryService.attemptDelivery(attempt, event);
        deliveryStore.recordOutcome(attempt.getId(), outcome);
    }
}