package com.invoiceapp.backend.notification.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class NotificationServiceTest {

    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        notificationService = new NotificationService();
    }

    @Test
    @DisplayName("createConnection should add emitter and return it")
    void create_connection_adds_emitter() {
        UUID userId = UUID.randomUUID();
        SseEmitter emitter = notificationService.createConnection(userId);

        assertNotNull(emitter, "Emitter should not be null");
        assertDoesNotThrow(() ->
                notificationService.sendStatusChange(userId, "INV-001", "ID-1", "PAID")
        );
    }

    @Test
    @DisplayName("sendStatusChange should not fail if user has no emitters")
    void send_status_change_handles_missing_user() {
        assertDoesNotThrow(() ->
                notificationService.sendStatusChange(UUID.randomUUID(), "INV-001", "ID-1", "PAID")
        );
    }
}