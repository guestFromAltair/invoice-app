package com.invoiceapp.backend.shared.audit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuditController.class)
@DisplayName("AuditController Mvc Endpoint Tests")
class AuditControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuditService auditService;

    @TestConfiguration
    @EnableMethodSecurity
    static class SecurityTestConfig {
        @Bean
        public SecurityFilterChain safetyChain(HttpSecurity http) throws Exception {
            return http.csrf(csrf -> csrf.disable())
                    .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                    .build();
        }
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("should allow access to users reading invoice histories")
    void should_allow_invoice_history_access() throws Exception {
        UUID id = UUID.randomUUID();
        when(auditService.getInvoiceHistory(id)).thenReturn(List.of());

        mockMvc.perform(get("/api/audit/invoices/" + id)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("should allow access to users reading client histories")
    void should_allow_client_history_access() throws Exception {
        UUID id = UUID.randomUUID();
        when(auditService.getClientHistory(id)).thenReturn(List.of());

        mockMvc.perform(get("/api/audit/clients/" + id)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("should reject regular users calling admin user path endpoints")
    void should_deny_user_history_for_non_admins() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(get("/api/audit/admin/users/" + id)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("should allow admins to access user history path endpoints and call underlying service layers")
    void should_allow_user_history_for_admins() throws Exception {
        UUID id = UUID.randomUUID();

        when(auditService.getEntityHistory("USER", id)).thenReturn(List.of());

        mockMvc.perform(get("/api/audit/admin/users/" + id)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        Mockito.verify(auditService, Mockito.times(1))
                .getEntityHistory("USER", id);
    }
}