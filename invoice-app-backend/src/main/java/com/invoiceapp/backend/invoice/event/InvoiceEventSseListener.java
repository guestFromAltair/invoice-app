package com.invoiceapp.backend.invoice.event;

import com.invoiceapp.backend.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;

@Component
@Profile("!test")
@RequiredArgsConstructor
@Slf4j
public class InvoiceEventSseListener {

    private static final String STATUS_CHANGED = "InvoiceStatusChanged";

    private final NotificationService notificationService;
    private final JsonMapper jsonMapper;

    @KafkaListener(
            topics = "${application.kafka.topic.invoice-events}",
            groupId = "#{@kafkaConsumerGroups.sseGroupId}"
    )
    public void onInvoiceEvent(ConsumerRecord<String, String> record) {
        String eventType = header(record);
        if (!STATUS_CHANGED.equals(eventType)) {
            return;
        }

        try {
            InvoiceStatusChangedEvent event = jsonMapper.readValue(record.value(), InvoiceStatusChangedEvent.class);
            notificationService.sendStatusChange(
                    event.changedBy(),
                    event.invoiceNumber(),
                    event.invoiceId().toString(),
                    event.newStatus()
            );
        } catch (Exception ex) {
            log.error("Failed to handle invoice event at partition={} offset={}",
                    record.partition(), record.offset(), ex);
        }
    }

    private String header(ConsumerRecord<String, String> record) {
        Header header = record.headers().lastHeader("eventType");
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }
}