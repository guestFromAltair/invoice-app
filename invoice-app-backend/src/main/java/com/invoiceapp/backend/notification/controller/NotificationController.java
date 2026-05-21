package com.invoiceapp.backend.notification.controller;

import com.invoiceapp.backend.auth.domain.User;
import com.invoiceapp.backend.auth.domain.UserRepository;
import com.invoiceapp.backend.notification.service.NotificationService;
import com.invoiceapp.backend.shared.security.CurrentUserResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Objects;
import java.util.UUID;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final CurrentUserResolver currentUserResolver;

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        User user = currentUserResolver.resolveUser();

        return notificationService.createConnection(user.getId());
    }
}