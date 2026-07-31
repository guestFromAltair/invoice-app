package com.invoiceapp.backend.invoice.event;

import com.invoiceapp.backend.config.JacksonTestConfig;
import com.invoiceapp.backend.config.PostgresTestContainer;
import com.invoiceapp.backend.notification.service.NotificationService;
import com.invoiceapp.backend.shared.outbox.OutboxEvent;
import com.invoiceapp.backend.shared.outbox.OutboxEventRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.kafka.KafkaContainer;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

@SpringBootTest(properties = "spring.kafka.consumer.auto-offset-reset=earliest")
@Import(JacksonTestConfig.class)
@DisplayName("Outbox → Kafka → SSE flow")
class InvoiceEventFlowIntegrationTest extends PostgresTestContainer {

    static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:3.8.1");

    static {
        KAFKA.start();
    }

    @DynamicPropertySource
    static void kafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }

    @MockitoBean
    private NotificationService notificationService;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Test
    @DisplayName("an InvoiceStatusChanged outbox row is relayed to Kafka and pushed over SSE")
    void status_change_event_reaches_the_sse_listener() {
        UUID invoiceId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        String payload = """
                {"invoiceId":"%s",
                 "invoiceNumber":"INV-2026-00042",
                 "oldStatus":"DRAFT",
                 "newStatus":"SENT",
                 "changedBy":"%s",
                 "ownerId":"%s",
                 "occurredAt":"2026-01-01T00:00:00Z"}
                """.formatted(invoiceId, userId, userId);

        OutboxEvent event = outboxEventRepository.save(OutboxEvent.builder()
                .topic("invoice.events")
                .aggregateType("INVOICE")
                .aggregateId(invoiceId)
                .eventType("InvoiceStatusChanged")
                .payload(payload)
                .build());

        verify(notificationService, timeout(20_000)).sendStatusChange(
                userId, "INV-2026-00042", invoiceId.toString(), "SENT");

        await().atMost(java.time.Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(outboxEventRepository.findById(event.getId()))
                        .get()
                        .extracting(OutboxEvent::getPublishedAt)
                        .isNotNull());
    }
}