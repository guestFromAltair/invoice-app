package com.invoiceapp.delivery.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
@Profile("!test")
public class KafkaTopicConfig {
    @Bean
    public NewTopic invoiceDeliveryDltTopic(
            @Value("${application.kafka.topic.invoice-delivery}") String topicName) {
        return TopicBuilder.name(topicName + ".DLT")
                .partitions(3)
                .replicas(1)
                .build();
    }
}