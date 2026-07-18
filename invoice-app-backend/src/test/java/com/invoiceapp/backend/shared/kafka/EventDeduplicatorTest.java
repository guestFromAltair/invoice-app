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
    @DisplayName("first sighting returns true and saves")
    void first_time() {
        UUID id = UUID.randomUUID();
        when(processedEventRepository.existsById(id)).thenReturn(false);

        boolean first = eventDeduplicator.markIfFirstTime(id, "sse-listener");

        assertThat(first).isTrue();
        verify(processedEventRepository).save(any(ProcessedEvent.class));
    }

    @Test
    @DisplayName("already-seen returns false and does not save")
    void already_seen() {
        UUID id = UUID.randomUUID();
        when(processedEventRepository.existsById(id)).thenReturn(true);

        boolean first = eventDeduplicator.markIfFirstTime(id, "sse-listener");

        assertThat(first).isFalse();
        verify(processedEventRepository, never()).save(any());
    }

    @Test
    @DisplayName("lost race on insert returns false")
    void lost_race() {
        UUID id = UUID.randomUUID();
        when(processedEventRepository.existsById(id)).thenReturn(false);
        when(processedEventRepository.save(any()))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));

        boolean first = eventDeduplicator.markIfFirstTime(id, "sse-listener");

        assertThat(first).isFalse();
    }
}