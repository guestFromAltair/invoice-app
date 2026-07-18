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

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean markIfFirstTime(UUID eventId, String consumer) {
        if (processedEventRepository.existsById(eventId)) {
            return false;
        }
        try {
            processedEventRepository.save(new ProcessedEvent(eventId, consumer, null));
            return true;
        } catch (DataIntegrityViolationException race) {
            return false;
        }
    }
}