package com.invoiceapp.backend.notification.controller;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.concurrent.CopyOnWriteArrayList;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final CopyOnWriteArrayList<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);

        emitters.add(emitter);

        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(e -> emitters.remove(emitter));

        return emitter;
    }

    public void sendStatusChange(String invoiceNumber, String invoiceId, String newStatus) {
        String data = String.format(
                "{\"type\":\"STATUS_CHANGED\",\"invoiceId\":\"%s\"," +
                        "\"invoiceNumber\":\"%s\",\"newStatus\":\"%s\"," +
                        "\"message\":\"Invoice %s is now %s\"}",
                invoiceId, invoiceNumber, newStatus, invoiceNumber, newStatus
        );

        emitters.forEach(emitter -> {
            try {
                emitter.send(SseEmitter.event().name("invoice-update").data(data));
            } catch (Exception e) {
                emitters.remove(emitter);
            }
        });
    }
}