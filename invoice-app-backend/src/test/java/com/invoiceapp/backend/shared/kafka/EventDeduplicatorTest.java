package com.invoiceapp.backend.shared.kafka;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("EventDeduplicator")
class EventDeduplicatorTest {

    @Mock
    private ProcessedEventRepository processedEventRepository;
    @InjectMocks
    private EventDeduplicator eventDeduplicator;

    @Test
    @DisplayName("alreadyProcessed returns true when the id exists")
    void already_processed_true() {
        UUID id = UUID.randomUUID();
        when(processedEventRepository.existsById(id)).thenReturn(true);

        assertThat(eventDeduplicator.alreadyProcessed(id, "sse-listener")).isTrue();
    }

    @Test
    @DisplayName("alreadyProcessed returns false when the id is new")
    void already_processed_false() {
        UUID id = UUID.randomUUID();
        when(processedEventRepository.existsById(id)).thenReturn(false);

        assertThat(eventDeduplicator.alreadyProcessed(id, "sse-listener")).isFalse();
    }

    @Test
    @DisplayName("markProcessed saves the id")
    void mark_saves() {
        UUID id = UUID.randomUUID();

        eventDeduplicator.markProcessed(id, "sse-listener");

        verify(processedEventRepository).save(any(ProcessedEvent.class));
    }

    @Test
    @DisplayName("markProcessed swallows a duplicate-key race")
    void mark_swallows_race() {
        UUID id = UUID.randomUUID();
        when(processedEventRepository.save(any()))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));

        assertThatNoException()
                .isThrownBy(() -> eventDeduplicator.markProcessed(id, "sse-listener"));
    }
}