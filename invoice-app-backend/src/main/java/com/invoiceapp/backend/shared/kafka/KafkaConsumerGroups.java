package com.invoiceapp.backend.shared.kafka;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component("kafkaConsumerGroups")
@Getter
public class KafkaConsumerGroups {

    private final String sseGroupId;

    public KafkaConsumerGroups(@Value("${application.kafka.consumer.sse-group-prefix}") String prefix) {
        this.sseGroupId = prefix + "-" + UUID.randomUUID();
    }
}