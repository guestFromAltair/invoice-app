package com.invoiceapp.delivery.event;

import com.invoiceapp.delivery.domain.DeliveryAttempt;
import com.invoiceapp.delivery.domain.DeliveryAttemptRepository;
import com.invoiceapp.delivery.domain.DeliveryStatus;
import com.invoiceapp.delivery.service.DeliveryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Component
@RequiredArgsConstructor
@Slf4j
public class InvoiceDeliveryListener {
    private final DeliveryAttemptRepository repository;
    private final DeliveryService deliveryService;
    private final JsonMapper jsonMapper;

    @KafkaListener(topics = "${application.kafka.topic.invoice-delivery}")
    @Transactional
    public void onInvoiceReadyForDelivery(ConsumerRecord<String, String> record) {
        InvoiceReadyForDeliveryEvent event = jsonMapper.readValue(record.value(), InvoiceReadyForDeliveryEvent.class);
        if (repository.existsByInvoiceId(event.invoiceId())) {
            log.info("Invoice {} already has a delivery record, skipping", event.invoiceNumber());
            return;
        }

        DeliveryAttempt attempt = DeliveryAttempt.builder()
                .invoiceId(event.invoiceId())
                .invoiceNumber(event.invoiceNumber())
                .ownerId(event.ownerId())
                .recipient(event.recipient().email())
                .status(DeliveryStatus.PENDING)
                .attempts(0)
                .payload(record.value())
                .build();

        try {
            repository.saveAndFlush(attempt);
        } catch (DataIntegrityViolationException race) {
            log.info("Invoice {} claimed by a concurrent delivery, skipping", event.invoiceNumber());
            return;
        }

        deliveryService.attemptDelivery(attempt, event);
        repository.save(attempt);
    }
}