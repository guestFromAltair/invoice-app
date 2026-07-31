package com.invoiceapp.backend.shared.outbox;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OutboxService {

    private final OutboxEventRepository outboxEventRepository;
    private final JsonMapper jsonMapper;

    public record OutboxMessage(UUID aggregateId, Object payload) {
    }

    @Value("${application.kafka.topic.invoice-events}")
    private String defaultTopic;

    @Transactional(propagation = Propagation.MANDATORY)
    public void publish(String aggregateType, UUID aggregateId, String eventType, Object payload) {
        doPublish(defaultTopic, aggregateType, aggregateId, eventType, payload);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void publishTo(String topic, String aggregateType, UUID aggregateId, String eventType, Object payload) {
        doPublish(topic, aggregateType, aggregateId, eventType, payload);
    }

    private void doPublish(String topic, String aggregateType, UUID aggregateId, String eventType, Object payload) {
        outboxEventRepository.save(OutboxEvent.builder()
                .topic(topic)
                .aggregateType(aggregateType)
                .aggregateId(aggregateId)
                .eventType(eventType)
                .payload(jsonMapper.writeValueAsString(payload))
                .build());
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void publishAll(String aggregateType, String eventType, List<OutboxMessage> messages) {
        doPublishAll(defaultTopic, aggregateType, eventType, messages);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void publishAllTo(String topic, String aggregateType, String eventType, List<OutboxMessage> messages) {
        doPublishAll(topic, aggregateType, eventType, messages);
    }

    private void doPublishAll(String topic, String aggregateType, String eventType, List<OutboxMessage> messages) {
        List<OutboxEvent> rows = messages.stream()
                .map(m -> OutboxEvent.builder()
                        .topic(topic)
                        .aggregateType(aggregateType)
                        .aggregateId(m.aggregateId())
                        .eventType(eventType)
                        .payload(jsonMapper.writeValueAsString(m.payload()))
                        .build())
                .toList();

        outboxEventRepository.saveAll(rows);
    }
}