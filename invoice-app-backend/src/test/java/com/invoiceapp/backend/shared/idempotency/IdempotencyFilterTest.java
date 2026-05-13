package com.invoiceapp.backend.shared.idempotency;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.invoiceapp.backend.auth.domain.User;
import com.invoiceapp.backend.auth.domain.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("IdempotencyFilter")
class IdempotencyFilterTest {
    @Mock
    private IdempotencyService idempotencyService;
    @Mock
    private UserRepository userRepository;
    @Mock
    private FilterChain filterChain;
    @Mock
    private Authentication authentication;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private IdempotencyFilter idempotencyFilter;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Should skip filter when Idempotency-Key header is missing")
    void shouldSkipWhenHeaderIsMissing() throws Exception {
        request.setMethod("POST");
        request.setRequestURI("/api/invoices");

        idempotencyFilter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(eq(request), any());
        verifyNoInteractions(idempotencyService);
    }

    @Test
    @DisplayName("Should skip filter when HTTP method is not POST")
    void shouldSkipWhenMethodIsNotPost() throws Exception {
        request.setMethod("GET");
        request.addHeader("Idempotency-Key", "test-key-123");

        idempotencyFilter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(eq(request), any());
        verifyNoInteractions(idempotencyService);
    }

    @Test
    @DisplayName("Should skip filter when user identity cannot be resolved")
    void shouldSkipWhenUserResolutionFails() throws Exception {
        request.setMethod("POST");
        request.addHeader("Idempotency-Key", "test-key-123");

        idempotencyFilter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(eq(request), any());
    }

    @Test
    @DisplayName("Should replay response when existing idempotent transaction is found")
    void shouldReplayStoredResponse() throws Exception {
        UUID userId = UUID.randomUUID();
        request.setMethod("POST");
        request.setRequestURI("/api/invoices");
        request.addHeader("Idempotency-Key", "key-xyz");

        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn("owner@example.com");
        SecurityContextHolder.getContext().setAuthentication(authentication);

        User mockUser = User.builder().id(userId).email("owner@example.com").build();
        when(userRepository.findByEmail("owner@example.com")).thenReturn(Optional.of(mockUser));

        Map<String, String> cachedBody = Map.of("id", "inv-1");
        IdempotencyService.StoredResponse storedResponse = new IdempotencyService.StoredResponse(201, cachedBody);

        when(idempotencyService.findExistingResponse("key-xyz", userId, "/api/invoices"))
                .thenReturn(Optional.of(storedResponse));

        idempotencyFilter.doFilterInternal(request, response, filterChain);

        verifyNoInteractions(filterChain);
        assertThat(response.getStatus()).isEqualTo(201);
        assertThat(response.getHeader("Idempotency-Replayed")).isEqualTo("true");
        assertThat(response.getContentAsString()).contains("inv-1");
    }

    @Test
    @DisplayName("Should catch the response stream out and save it on fresh successes (< 500)")
    void shouldStoreFreshSuccessfulResponse() throws Exception {
        UUID userId = UUID.randomUUID();
        request.setMethod("POST");
        request.setRequestURI("/api/invoices");
        request.addHeader("Idempotency-Key", "fresh-key");

        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn("owner@example.com");
        SecurityContextHolder.getContext().setAuthentication(authentication);

        User mockUser = User.builder().id(userId).email("owner@example.com").build();
        when(userRepository.findByEmail("owner@example.com")).thenReturn(Optional.of(mockUser));
        when(idempotencyService.findExistingResponse("fresh-key", userId, "/api/invoices"))
                .thenReturn(Optional.empty());

        doAnswer(invocation -> {
            HttpServletResponse chainResponse = invocation.getArgument(1);
            chainResponse.setStatus(201);
            chainResponse.getWriter().write("{\"status\":\"success\"}");
            return null;
        }).when(filterChain).doFilter(eq(request), any());

        idempotencyFilter.doFilterInternal(request, response, filterChain);

        verify(idempotencyService).storeResponse(
                eq("fresh-key"), eq(userId), eq("/api/invoices"),
                eq(201), any()
        );
    }

    @Test
    @DisplayName("Should skip storage logic if downstream action returns a 500 error state")
    void shouldIgnoreServerErrorsFromStorage() throws Exception {
        UUID userId = UUID.randomUUID();
        request.setMethod("POST");
        request.addHeader("Idempotency-Key", "error-key");

        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn("owner@example.com");
        SecurityContextHolder.getContext().setAuthentication(authentication);
        when(userRepository.findByEmail("owner@example.com")).thenReturn(Optional.of(User.builder().id(userId).build()));
        when(idempotencyService.findExistingResponse("error-key", userId, "")).thenReturn(Optional.empty());

        doAnswer(invocation -> {
            HttpServletResponse chainResponse = invocation.getArgument(1);
            chainResponse.setStatus(500);
            chainResponse.getWriter().write("{\"error\":\"Internal DB Fail\"}");
            return null;
        }).when(filterChain).doFilter(eq(request), any());

        idempotencyFilter.doFilterInternal(request, response, filterChain);

        verify(idempotencyService, times(0)).storeResponse(any(), any(), any(), anyInt(), any());
    }

    @Test
    @DisplayName("Should gracefully catch and log Jackson read validation exceptions")
    void shouldCatchParsingExceptionsGracefully() throws Exception {
        UUID userId = UUID.randomUUID();
        request.setMethod("POST");
        request.addHeader("Idempotency-Key", "malformed-key");

        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn("owner@example.com");
        SecurityContextHolder.getContext().setAuthentication(authentication);
        when(userRepository.findByEmail("owner@example.com")).thenReturn(Optional.of(User.builder().id(userId).build()));
        when(idempotencyService.findExistingResponse("malformed-key", userId, "")).thenReturn(Optional.empty());

        doAnswer(invocation -> {
            HttpServletResponse chainResponse = invocation.getArgument(1);
            chainResponse.setStatus(200);
            chainResponse.getWriter().write("{broken-json-not-valid}");
            return null;
        }).when(filterChain).doFilter(eq(request), any());

        // Ensure filter absorbs Jackson parsing problems without throwing a runtime break
        idempotencyFilter.doFilterInternal(request, response, filterChain);

        verify(idempotencyService, times(0)).storeResponse(any(), any(), any(), anyInt(), any());
    }
}