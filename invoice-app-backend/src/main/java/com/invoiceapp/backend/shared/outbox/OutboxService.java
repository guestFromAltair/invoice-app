package com.invoiceapp.backend.shared.outbox;

import lombok.RequiredArgsConstructor;
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

    public record OutboxMessage(UUID aggregateId, Object payload) {}

    @Transactional(propagation = Propagation.MANDATORY)
    public void publish(String aggregateType, UUID aggregateId, String eventType, Object payload) {
        OutboxEvent event = OutboxEvent.builder()
                .aggregateType(aggregateType)
                .aggregateId(aggregateId)
                .eventType(eventType)
                .payload(jsonMapper.writeValueAsString(payload))
                .build();

        outboxEventRepository.save(event);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void publishAll(String aggregateType, String eventType, List<OutboxMessage> messages) {
        List<OutboxEvent> rows = messages.stream()
                .map(m -> OutboxEvent.builder()
                        .aggregateType(aggregateType)
                        .aggregateId(m.aggregateId())
                        .eventType(eventType)
                        .payload(jsonMapper.writeValueAsString(m.payload()))
                        .build())
                .toList();

        outboxEventRepository.saveAll(rows);
    }
}