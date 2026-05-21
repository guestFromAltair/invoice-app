package com.invoiceapp.backend.shared.reconciliation;

import com.invoiceapp.backend.auth.service.JwtService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReconciliationController.class)
@AutoConfigureMockMvc(addFilters = false) // Bypasses Spring Security filters for slice granularity
@DisplayName("ReconciliationController Endpoint Tests")
class ReconciliationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean private ReconciliationService reconciliationService;
    @MockitoBean private JwtService jwtService;

    @Test
    @DisplayName("GET /api/admin/reconciliation/report should return generated payload matrix successfully")
    void get_report_returns_payload_successfully() throws Exception {
        ReconciliationService.ReconciliationReport mockReport = new ReconciliationService.ReconciliationReport(
                LocalDate.now(),
                10,
                0,
                List.of(),
                "✅ Clean Report Summary"
        );

        when(reconciliationService.runReconciliation()).thenReturn(mockReport);

        mockMvc.perform(get("/api/admin/reconciliation/report")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalInvoicesChecked").value(10))
                .andExpect(jsonPath("$.issueCount").value(0))
                .andExpect(jsonPath("$.summary").value("✅ Clean Report Summary"));
    }
}