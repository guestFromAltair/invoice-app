package com.invoiceapp.backend.shared.audit;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;
import com.invoiceapp.backend.auth.domain.User;
import com.invoiceapp.backend.client.domain.ClientRepository;
import com.invoiceapp.backend.invoice.domain.InvoiceRepository;
import com.invoiceapp.backend.shared.exception.InvoiceAppException;
import com.invoiceapp.backend.shared.security.CurrentUserResolver;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditService {

    private final AuditLogRepository auditLogRepository;
    private final JsonMapper jsonMapper;
    private final CurrentUserResolver currentUserResolver;
    private final InvoiceRepository invoiceRepository;
    private final ClientRepository clientRepository;

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(
            String entityType,
            UUID entityId,
            String action,
            Object oldValue,
            Object newValue,
            UUID performedBy
    ) {
        try {
            String oldJson = toJson(oldValue);
            String newJson = toJson(newValue);
            String ipAddress = resolveIpAddress();
            String requestId = resolveRequestId();

            AuditLog entry = AuditLog.builder()
                    .entityType(entityType)
                    .entityId(entityId)
                    .action(action)
                    .oldValue(oldJson)
                    .newValue(newJson)
                    .performedBy(performedBy)
                    .performedAt(Instant.now())
                    .ipAddress(ipAddress)
                    .requestId(requestId)
                    .build();

            auditLogRepository.save(entry);
        } catch (Exception e) {
            log.error(
                    "AUDIT LOG FAILURE — entityType={} entityId={} action={} user={}",
                    entityType, entityId, action, performedBy, e
            );
        }
    }

    @Transactional(readOnly = true)
    public List<AuditLog> getInvoiceHistory(UUID invoiceId) {
        User user = currentUserResolver.resolveUser();

        invoiceRepository.findByIdAndCreatedById(invoiceId, user.getId())
                .orElseThrow(() -> new InvoiceAppException(
                        "Invoice not found", HttpStatus.NOT_FOUND
                ));

        return auditLogRepository
                .findAllByEntityTypeAndEntityIdOrderByPerformedAtAsc("INVOICE", invoiceId);
    }

    @Transactional(readOnly = true)
    public List<AuditLog> getClientHistory(UUID clientId) {
        User user = currentUserResolver.resolveUser();

        clientRepository.findByIdAndOwnerId(clientId, user.getId())
                .orElseThrow(() -> new InvoiceAppException(
                        "Client not found", HttpStatus.NOT_FOUND
                ));

        return auditLogRepository
                .findAllByEntityTypeAndEntityIdOrderByPerformedAtAsc(
                        "CLIENT", clientId
                );
    }

    @Transactional(readOnly = true)
    public List<AuditLog> getEntityHistory(String entityType, UUID entityId) {
        return auditLogRepository.findAllByEntityTypeAndEntityIdOrderByPerformedAtAsc(entityType, entityId);
    }

    private String toJson(Object value) {
        if (value == null) return null;
        try {
            return jsonMapper.writeValueAsString(value);
        } catch (JacksonException e) {
            log.warn("Failed to serialize audit value to JSON", e);
            return "{\"error\":\"serialization_failed\"}";
        }
    }

    private String resolveIpAddress() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
            HttpServletRequest request = attrs.getRequest();

            String forwardedFor = request.getHeader("X-Forwarded-For");
            if (forwardedFor != null && !forwardedFor.isBlank()) {
                return forwardedFor.split(",")[0].trim();
            }
            return request.getRemoteAddr();
        } catch (Exception e) {
            return null;
        }
    }

    private String resolveRequestId() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
            HttpServletRequest request = attrs.getRequest();

            String requestId = request.getHeader("X-Request-ID");
            if (requestId != null && !requestId.isBlank()) {
                return requestId;
            }
            return request.getSession(false) != null
                    ? request.getSession(false).getId().substring(0, 8)
                    : null;
        } catch (Exception e) {
            return null;
        }
    }
}