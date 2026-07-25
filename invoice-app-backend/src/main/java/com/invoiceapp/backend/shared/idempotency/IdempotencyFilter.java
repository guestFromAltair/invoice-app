package com.invoiceapp.backend.shared.idempotency;

import tools.jackson.databind.json.JsonMapper;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

@RequiredArgsConstructor
@Slf4j
public class IdempotencyFilter extends OncePerRequestFilter {

    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

    private final IdempotencyService idempotencyService;
    private final UserRepository userRepository;
    private final JsonMapper jsonMapper;

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
            if (stored.status() == HttpStatus.ACCEPTED.value()) {
                log.warn("Concurrent idempotent request intercepted. Key: {} is still processing.", idempotencyKey);
                sendErrorResponse(response, HttpStatus.CONFLICT, "Request is already being processed. Please wait.");
                return;
            }

            log.info("Replaying idempotent response for key: {} path: {}", idempotencyKey, requestPath);
            response.setStatus(stored.status());
            response.setHeader("Idempotency-Replayed", "true");
            if (stored.body() != null) {
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                jsonMapper.writeValue(response.getWriter(), stored.body());
            }
            return;
        }

        boolean lockAcquired = idempotencyService.tryLock(idempotencyKey, userId, requestPath);
        if (!lockAcquired) {
            sendErrorResponse(response, HttpStatus.CONFLICT, "Duplicate request detected.");
            return;
        }

        ContentCachingResponseWrapper responseWrapper = new ContentCachingResponseWrapper(response);
        boolean committed = false;

        try {
            filterChain.doFilter(request, responseWrapper);

            int status = responseWrapper.getStatus();
            byte[] responseBody = responseWrapper.getContentAsByteArray();

            Object parsedBody = responseBody.length > 0
                    ? jsonMapper.readValue(responseBody, Object.class)
                    : null;

            idempotencyService.commitResponse(idempotencyKey, userId, requestPath, status, parsedBody);
            committed = true;
        } catch (Exception filterEx) {
            log.error("Execution crashed. Committing error state.", filterEx);
            idempotencyService.commitResponse(idempotencyKey, userId, requestPath, 500, Map.of("error", "System crash"));
            committed = true;
            throw filterEx;
        } finally {
            // If 'committed' is still false, it means something bad happened. We must resolve the lock
            if (!committed) {
                log.warn("CRITICAL: Request ended without formal commit. Releasing hanging lock for key: {}", idempotencyKey);
                idempotencyService.commitResponse(
                        idempotencyKey, userId, requestPath,
                        500, Map.of("error", "Transaction terminated unexpectedly")
                );
            }
            responseWrapper.copyBodyToResponse();
        }
    }

    private void sendErrorResponse(HttpServletResponse response, HttpStatus status, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        Map<String, String> errorDetails = Map.of("title", status.getReasonPhrase(), "detail", message);
        jsonMapper.writeValue(response.getWriter(), errorDetails);
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