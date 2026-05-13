package com.invoiceapp.backend.shared.idempotency;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.invoiceapp.backend.auth.domain.User;
import com.invoiceapp.backend.auth.domain.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.util.UUID;

@RequiredArgsConstructor
@Slf4j
public class IdempotencyFilter extends OncePerRequestFilter {

    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

    private final IdempotencyService idempotencyService;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        String idempotencyKey = request.getHeader(IDEMPOTENCY_KEY_HEADER);
        if (idempotencyKey == null || !HttpMethod.POST.matches(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        UUID userId = resolveUserId();
        if (userId == null) {
            filterChain.doFilter(request, response);
            return;
        }

        String requestPath = request.getRequestURI();

        var existingResponse = idempotencyService.findExistingResponse(idempotencyKey, userId, requestPath);
        if (existingResponse.isPresent()) {
            IdempotencyService.StoredResponse stored = existingResponse.get();

            log.info("Replaying idempotent response for key: {} path: {}", idempotencyKey, requestPath);

            response.setStatus(stored.status());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setHeader("Idempotency-Replayed", "true");
            objectMapper.writeValue(response.getWriter(), stored.body());
            return;
        }

        ContentCachingResponseWrapper responseWrapper = new ContentCachingResponseWrapper(response);

        filterChain.doFilter(request, responseWrapper);

        int status = responseWrapper.getStatus();
        byte[] responseBody = responseWrapper.getContentAsByteArray();
        if (responseBody.length > 0) {
            try {
                Object parsedBody = objectMapper.readValue(responseBody, Object.class);
                if (status < 500) {
                    idempotencyService.storeResponse(
                            idempotencyKey, userId, requestPath,
                            status, parsedBody
                    );
                }
            } catch (Exception e) {
                log.warn("Could not parse response body for idempotency storage", e);
            }
        }

        responseWrapper.copyBodyToResponse();
    }

    private UUID resolveUserId() {
        try {
            var auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !auth.isAuthenticated()) return null;
            String email = auth.getName();
            return userRepository.findByEmail(email).map(User::getId).orElse(null);
        } catch (Exception e) {
            return null;
        }
    }
}