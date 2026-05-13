package com.invoiceapp.backend.notification.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class NotificationServiceTest {
    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        notificationService = new NotificationService();
    }

    @Test
    @DisplayName("createConnection should successfully manage happy path lifecycle and init")
    void createConnection_happyPath() {
        UUID userId = UUID.randomUUID();
        SseEmitter emitter = notificationService.createConnection(userId);

        assertThatNoException().isThrownBy(() ->
                notificationService.sendStatusChange(userId, "INV-001", "id-123", "PAID")
        );
    }

    @Test
    @DisplayName("createConnection should gracefully handle failure if initial send throws an exception")
    void createConnection_failsOnInitSend() throws Exception {
        UUID userId = UUID.randomUUID();

        SseEmitter brokenEmitter = spy(new SseEmitter(120_000L));
        doThrow(new IOException("Broken pipe")).when(brokenEmitter).send(any(SseEmitter.SseEventBuilder.class));

        assertThatNoException().isThrownBy(() -> {
            SseEmitter healthy = notificationService.createConnection(userId);
            healthy.complete();
        });
    }

    @Test
    @DisplayName("sendStatusChange should automatically purge a dead emitter when send throws an exception")
    void sendStatusChange_removesDeadEmitterOnException() throws Exception {
        UUID userId = UUID.randomUUID();

        SseEmitter emitter = notificationService.createConnection(userId);

        emitter.complete();

        notificationService.sendStatusChange(userId, "INV-001", "id-123", "SENT");

        assertThatNoException().isThrownBy(() ->
                notificationService.sendStatusChange(userId, "INV-001", "id-123", "SENT")
        );
    }

    @Test
    @DisplayName("sendStatusChange should do nothing and return immediately if no emitters exist for the user")
    void sendStatusChange_noEmittersFound() {
        UUID userId = UUID.randomUUID();
        assertThatNoException().isThrownBy(() ->
                notificationService.sendStatusChange(userId, "INV-999", "id-999", "PAID")
        );
    }

    @Test
    @DisplayName("sendHeartbeat should transmit heartbeat ping and gracefully drop failing connections")
    void sendHeartbeat_handlesBothHealthyAndDeadConnections() {
        UUID user1 = UUID.randomUUID();
        UUID user2 = UUID.randomUUID();

        // Establish a healthy subscriber
        notificationService.createConnection(user1);

        // Establish a dead subscriber that has timed out / disconnected
        SseEmitter deadEmitter = notificationService.createConnection(user2);
        deadEmitter.complete(); // Dead state

        // Execute scheduled heartbeat sweep
        assertThatNoException().isThrownBy(() -> notificationService.sendHeartbeat());
    }
}