package com.invoiceapp.backend.shared.outbox;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OutboxRelay")
class OutboxRelayTest {

    @Mock private OutboxEventRepository outboxEventRepository;
    @Mock private KafkaTemplate<String, String> kafkaTemplate;

    @Captor private ArgumentCaptor<ProducerRecord<String, String>> recordCaptor;

    @InjectMocks private OutboxRelay outboxRelay;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(outboxRelay, "topic", "invoice.events");
        ReflectionTestUtils.setField(outboxRelay, "batchSize", 100);
    }

    private void stubSuccessfulSend() {
        SendResult<String, String> mockSendResult = mock();
        when(kafkaTemplate.send(ArgumentMatchers.<ProducerRecord<String, String>>any()))
                .thenReturn(CompletableFuture.completedFuture(mockSendResult));
    }

    private OutboxEvent event(String type) {
        return OutboxEvent.builder()
                .id(UUID.randomUUID())
                .aggregateType("INVOICE")
                .aggregateId(UUID.randomUUID())
                .eventType(type)
                .payload("{\"hello\":\"world\"}")
                .build();
    }

    @Test
    @DisplayName("publishes each unpublished event and stamps published_at")
    void publishes_and_marks() {
        OutboxEvent e1 = event("InvoiceCreated");
        OutboxEvent e2 = event("PaymentRecorded");
        when(outboxEventRepository.findUnpublishedBatch(100)).thenReturn(List.of(e1, e2));
        stubSuccessfulSend();

        outboxRelay.publishPending();

        verify(kafkaTemplate, times(2)).send(ArgumentMatchers.<ProducerRecord<String, String>>any());
        assertThat(e1.getPublishedAt()).isNotNull();
        assertThat(e2.getPublishedAt()).isNotNull();
        verify(outboxEventRepository).saveAll(List.of(e1, e2));
    }

    @Test
    @DisplayName("keys by aggregateId, values with payload, carries headers")
    void builds_correct_record() {
        OutboxEvent e = event("InvoiceCreated");
        when(outboxEventRepository.findUnpublishedBatch(100)).thenReturn(List.of(e));
        stubSuccessfulSend();

        outboxRelay.publishPending();

        verify(kafkaTemplate).send(recordCaptor.capture());
        ProducerRecord<String, String> record = recordCaptor.getValue();

        assertThat(record.topic()).isEqualTo("invoice.events");
        assertThat(record.key()).isEqualTo(e.getAggregateId().toString());
        assertThat(record.value()).isEqualTo(e.getPayload());
        assertThat(new String(record.headers().lastHeader("eventId").value()))
                .isEqualTo(e.getId().toString());
        assertThat(new String(record.headers().lastHeader("eventType").value()))
                .isEqualTo("InvoiceCreated");
    }

    @Test
    @DisplayName("leaves published_at null and does not save when the send fails")
    void retries_on_failure() {
        OutboxEvent e = event("InvoiceCreated");
        when(outboxEventRepository.findUnpublishedBatch(100)).thenReturn(List.of(e));

        CompletableFuture<SendResult<String, String>> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("broker down"));

        when(kafkaTemplate.send(ArgumentMatchers.<ProducerRecord<String, String>>any())).thenReturn(failed);

        outboxRelay.publishPending();

        assertThat(e.getPublishedAt()).isNull();
        verify(outboxEventRepository, never()).saveAll(ArgumentMatchers.<List<OutboxEvent>>any());
    }

    @Test
    @DisplayName("saves successful sends even if some events in the batch fail")
    void partial_failure_behavior() {
        OutboxEvent e1 = event("InvoiceCreated");
        OutboxEvent e2 = event("PaymentRecorded");
        when(outboxEventRepository.findUnpublishedBatch(100)).thenReturn(List.of(e1, e2));

        SendResult<String, String> mockSendResult = mock();
        CompletableFuture<SendResult<String, String>> success =
                CompletableFuture.completedFuture(mockSendResult);

        CompletableFuture<SendResult<String, String>> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("broker down"));

        when(kafkaTemplate.send(ArgumentMatchers.<ProducerRecord<String, String>>any()))
                .thenReturn(success)
                .thenReturn(failed);

        outboxRelay.publishPending();

        verify(kafkaTemplate, times(2)).send(ArgumentMatchers.<ProducerRecord<String, String>>any());

        assertThat(e1.getPublishedAt()).isNotNull();
        assertThat(e2.getPublishedAt()).isNull();

        verify(outboxEventRepository).saveAll(List.of(e1));
    }

    @Test
    @DisplayName("does nothing when there are no unpublished events")
    void empty_batch_is_a_noop() {
        when(outboxEventRepository.findUnpublishedBatch(100)).thenReturn(List.of());

        outboxRelay.publishPending();

        verifyNoInteractions(kafkaTemplate);
        verify(outboxEventRepository, never()).saveAll(ArgumentMatchers.<List<OutboxEvent>>any());
    }
}