package com.invoiceapp.backend.invoice.event;

import com.invoiceapp.backend.notification.service.NotificationService;
import com.invoiceapp.backend.shared.kafka.ConsumerLagMetrics;
import com.invoiceapp.backend.shared.kafka.EventDeduplicator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Component
@Profile("!test")
@RequiredArgsConstructor
@Slf4j
public class InvoiceEventSseListener {

    private static final String STATUS_CHANGED = "InvoiceStatusChanged";
    private static final String CONSUMER = "sse-listener";

    private final NotificationService notificationService;
    private final EventDeduplicator eventDeduplicator;
    private final JsonMapper jsonMapper;
    private final ConsumerLagMetrics consumerLagMetrics;

    @KafkaListener(
            topics = "${application.kafka.topic.invoice-events}",
            groupId = "#{@kafkaConsumerGroups.sseGroupId}"
    )
    public void onInvoiceEvent(ConsumerRecord<String, String> record, Consumer<?, ?> consumer) {
        recordLag(consumer);

        if (!STATUS_CHANGED.equals(header(record, "eventType"))) {
            return;
        }

        String eventId = header(record, "eventId");
        if (eventId != null && eventDeduplicator.alreadyProcessed(UUID.fromString(eventId), CONSUMER)) {
            log.debug("Skipping already-processed event {}", eventId);
            return;
        }

        InvoiceStatusChangedEvent event = jsonMapper.readValue(record.value(), InvoiceStatusChangedEvent.class);
        notificationService.sendStatusChange(
                event.ownerId(),
                event.invoiceNumber(),
                event.invoiceId().toString(),
                event.newStatus()
        );

        if (eventId != null) {
            try {
                eventDeduplicator.markProcessed(UUID.fromString(eventId), CONSUMER);
            } catch (Exception ex) {
                log.warn("Handled event {} but could not record it as processed", eventId, ex);
            }
        }
    }

    private String header(ConsumerRecord<String, String> record, String name) {
        Header header = record.headers().lastHeader(name);
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }

    private void recordLag(Consumer<?, ?> consumer) {
        long maxLag = consumer.metrics().entrySet().stream()
                .filter(e -> "records-lag-max".equals(e.getKey().name()))
                .map(e -> e.getValue().metricValue())
                .filter(v -> v instanceof Number)
                .mapToLong(v -> (long) ((Number) v).doubleValue())
                .max()
                .orElse(0L);

        consumerLagMetrics.update(maxLag);
    }
}