package com.invoiceapp.backend.shared.ratelimit;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RateLimitFilter")
class RateLimitFilterTest {

    @Mock private RateLimiterService rateLimiterService;
    @Mock private FilterChain filterChain;

    private RateLimitFilter filter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        filter = new RateLimitFilter(rateLimiterService, new JsonMapper(), new SimpleMeterRegistry());
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        request.setRequestURI("/api/invoices");
        request.setMethod("POST");
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("passes the request through when allowed")
    void allows() throws Exception {
        when(rateLimiterService.tryConsume(any()))
                .thenReturn(new RateLimiterService.Decision(true, 59, 0, 60));

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(response.getHeader("X-RateLimit-Remaining")).isEqualTo("59");
    }

    @Test
    @DisplayName("returns 429 with Retry-After when blocked")
    void blocks() throws Exception {
        when(rateLimiterService.tryConsume(any()))
                .thenReturn(new RateLimiterService.Decision(false, 0, 5, 60));

        filter.doFilterInternal(request, response, filterChain);

        verifyNoInteractions(filterChain);
        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getHeader("Retry-After")).isEqualTo("5");
    }

    @Test
    @DisplayName("skips actuator, OPTIONS and the SSE stream")
    void skips() {
        request.setRequestURI("/actuator/prometheus");
        assertThat(filter.shouldNotFilter(request)).isTrue();

        request.setRequestURI("/api/invoices");
        request.setMethod("OPTIONS");
        assertThat(filter.shouldNotFilter(request)).isTrue();

        request.setMethod("GET");
        request.setRequestURI("/api/notifications/stream");
        assertThat(filter.shouldNotFilter(request)).isTrue();
    }
}