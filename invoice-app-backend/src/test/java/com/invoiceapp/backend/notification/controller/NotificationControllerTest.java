package com.invoiceapp.backend.notification.controller;

import com.invoiceapp.backend.auth.domain.User;
import com.invoiceapp.backend.auth.domain.UserRepository;
import com.invoiceapp.backend.auth.service.JwtService;
import com.invoiceapp.backend.notification.service.NotificationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(NotificationController.class)
@DisplayName("NotificationController")
class NotificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NotificationService notificationService;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private JwtService jwtService;

    @Test
    @WithMockUser(username = "test@example.com")
    @DisplayName("GET /api/notifications/stream should call service with correct userId")
    void stream_establishes_connection() throws Exception {
        UUID mockUserId = UUID.randomUUID();
        User mockUser = new User();
        mockUser.setId(mockUserId);
        mockUser.setEmail("test@example.com");

        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(mockUser));
        when(notificationService.createConnection(mockUserId)).thenReturn(new SseEmitter());

        mockMvc.perform(get("/api/notifications/stream"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.TEXT_EVENT_STREAM_VALUE));

        verify(notificationService, times(1)).createConnection(mockUserId);
    }

    @Test
    @DisplayName("GET /api/notifications/stream should return 401 when unauthorized")
    void stream_unauthorized() throws Exception {
        mockMvc.perform(get("/api/notifications/stream")).andExpect(status().isUnauthorized());
    }
}