package com.invoiceapp.backend.shared.reconciliation;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/reconciliation")
@RequiredArgsConstructor
public class ReconciliationController {

    private final ReconciliationService reconciliationService;

    @GetMapping("/report")
    @PreAuthorize("hasRole('ADMIN')")
    public ReconciliationService.ReconciliationReport getReport() {
        return reconciliationService.runReconciliation();
    }
}