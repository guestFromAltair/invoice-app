package com.invoiceapp.backend.shared.outbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxRelay {

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Value("${application.kafka.topic.invoice-events}")
    private String topic;

    @Value("${application.outbox.relay.batch-size}")
    private int batchSize;

    private record EventSendPair(
            OutboxEvent event,
            CompletableFuture<SendResult<String, String>> future
    ) {}

    @Scheduled(fixedDelayString = "${application.outbox.relay.poll-interval-ms}")
    @Transactional
    public void publishPending() {
        List<OutboxEvent> batch = outboxEventRepository.findUnpublishedBatch(batchSize);
        if (batch.isEmpty()) {
            return;
        }

        List<EventSendPair> pairs = new ArrayList<>(batch.size());
        for (OutboxEvent event : batch) {
            pairs.add(new EventSendPair(event, sendToKafka(event)));
        }

        List<OutboxEvent> published = new ArrayList<>();

        for (EventSendPair pair : pairs) {
            try {
                pair.future().get();
                pair.event().setPublishedAt(Instant.now());
                published.add(pair.event());
            } catch (Exception ex) {
                log.error("Failed to publish outbox event {} (type={}), will retry next poll",
                        pair.event().getId(), pair.event().getEventType(), ex);
            }
        }

        if (!published.isEmpty()) {
            outboxEventRepository.saveAll(published);
            log.debug("Published {} outbox event(s) to topic {}", published.size(), topic);
        }
    }

    private CompletableFuture<SendResult<String, String>> sendToKafka(OutboxEvent event) {
        ProducerRecord<String, String> record = new ProducerRecord<>(
                topic,
                null,
                event.getAggregateId().toString(),
                event.getPayload()
        );
        record.headers().add("eventId", event.getId().toString().getBytes(StandardCharsets.UTF_8));
        record.headers().add("eventType", event.getEventType().getBytes(StandardCharsets.UTF_8));
        return kafkaTemplate.send(record);
    }
}