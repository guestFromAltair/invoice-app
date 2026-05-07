package com.invoiceapp.backend.notification.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
@Slf4j
@RequiredArgsConstructor
public class NotificationService {

    private final Map<UUID, CopyOnWriteArrayList<SseEmitter>> userEmitters = new ConcurrentHashMap<>();

    public SseEmitter createConnection(UUID userId) {
        SseEmitter emitter = new SseEmitter(120_000L);

        userEmitters.computeIfAbsent(userId, k -> new CopyOnWriteArrayList<>()).add(emitter);

        Runnable removeEmitter = () -> {
            List<SseEmitter> emitters = userEmitters.get(userId);
            if (emitters != null) {
                emitters.remove(emitter);
                if (emitters.isEmpty()) {
                    userEmitters.remove(userId);
                }
            }
        };

        emitter.onCompletion(removeEmitter);
        emitter.onTimeout(removeEmitter);
        emitter.onError(e -> removeEmitter.run());

        try {
            emitter.send(SseEmitter.event().name("init").data("connection-established"));
        } catch (Exception e) {
            removeEmitter.run();
        }

        return emitter;
    }

    public void sendStatusChange(UUID userId, String invoiceNumber, String invoiceId, String newStatus) {
        List<SseEmitter> emitters = userEmitters.get(userId);
        if (emitters == null || emitters.isEmpty()) return;

        Map<String, String> payload = Map.of(
                "type", "STATUS_CHANGED",
                "invoiceId", invoiceId,
                "invoiceNumber", invoiceNumber,
                "newStatus", newStatus
        );

        emitters.forEach(emitter -> {
            try {
                emitter.send(SseEmitter.event().name("invoice-update").data(payload));
            } catch (Exception e) {
                emitters.remove(emitter);
            }
        });
    }

    @Scheduled(fixedRate = 15000)
    public void sendHeartbeat() {
        userEmitters.forEach((userId, emitters) -> {
            emitters.forEach(emitter -> {
                try {
                    emitter.send(SseEmitter.event().comment("heartbeat"));
                } catch (Exception e) {
                    log.info("Heartbeat failed for user {}, removing emitter", userId);
                    emitter.complete();
                }
            });
        });
    }
}
