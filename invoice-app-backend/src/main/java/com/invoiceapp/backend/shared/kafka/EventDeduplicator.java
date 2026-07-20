package com.invoiceapp.backend.shared.kafka;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EventDeduplicator {

    private final ProcessedEventRepository processedEventRepository;

    @Transactional(readOnly = true)
    public boolean alreadyProcessed(UUID eventId, String consumer) {
        return processedEventRepository.existsById(eventId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markProcessed(UUID eventId, String consumer) {
        try {
            processedEventRepository.save(new ProcessedEvent(eventId, consumer, null));
        } catch (DataIntegrityViolationException alreadyThere) {
            // Someone else marked it first, so there is nothing to do.
        }
    }
}