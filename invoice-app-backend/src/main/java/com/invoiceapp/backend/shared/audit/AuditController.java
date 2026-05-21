package com.invoiceapp.backend.shared.audit;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/audit")
@RequiredArgsConstructor
public class AuditController {

    private final AuditService auditService;

    @GetMapping("/invoices/{invoiceId}")
    public List<AuditLog> getInvoiceHistory(@PathVariable UUID invoiceId) {
        return auditService.getInvoiceHistory(invoiceId);
    }

    @GetMapping("/clients/{clientId}")
    public List<AuditLog> getClientHistory(@PathVariable UUID clientId) {
        return auditService.getClientHistory(clientId);
    }

    @GetMapping("/admin/users/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public List<AuditLog> getUserHistory(@PathVariable UUID userId) {
        return auditService.getEntityHistory("USER", userId);
    }
}