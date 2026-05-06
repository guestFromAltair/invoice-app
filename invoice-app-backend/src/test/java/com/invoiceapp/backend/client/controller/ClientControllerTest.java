package com.invoiceapp.backend.client.controller;

import com.invoiceapp.backend.auth.domain.UserRepository;
import com.invoiceapp.backend.auth.service.JwtService;
import com.invoiceapp.backend.client.service.ClientService;
import com.invoiceapp.backend.shared.exception.InvoiceAppException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ClientController.class)
@DisplayName("ClientController")
class ClientControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ClientService clientService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private UserRepository userRepository;

    @Test
    @WithMockUser
    @DisplayName("GET /api/clients should return paginated list")
    void find_all_returns_200() throws Exception {
        ClientService.ClientResponse mockClient = new ClientService.ClientResponse(
                UUID.randomUUID(), "Acme Corp", "contact@acme.com", null, null, null, null
        );

        PageRequest pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.ASC, "name"));
        when(clientService.findAll(pageable)).thenReturn(new PageImpl<>(List.of(mockClient)));

        mockMvc.perform(get("/api/clients"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("Acme Corp"));
    }

    @Test
    @WithMockUser
    @DisplayName("POST /api/clients should return 201 when valid")
    void create_returns_201() throws Exception {
        String json = """
                {
                    "name": "Global Tech",
                    "email": "info@globaltech.com"
                }
                """;

        ClientService.ClientResponse mockResponse = new ClientService.ClientResponse(
                UUID.randomUUID(), "Global Tech", "info@globaltech.com", null, null, null, null
        );

        when(clientService.create(any(ClientService.ClientRequest.class))).thenReturn(mockResponse);

        mockMvc.perform(post("/api/clients")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Global Tech"));
    }

    @Test
    @WithMockUser
    @DisplayName("POST /api/clients should return 400 when name is blank")
    void create_returns_400_for_invalid_data() throws Exception {
        String invalidJson = """
                {
                    "name": "",
                    "email": "bad-request@test.com"
                }
                """;

        mockMvc.perform(post("/api/clients")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").exists());
    }

    @Test
    @WithMockUser
    @DisplayName("PUT /api/clients/{id} should update client")
    void update_returns_200() throws Exception {
        UUID id = UUID.randomUUID();
        String json = "{\"name\": \"Updated Name\"}";

        ClientService.ClientResponse mockResponse = new ClientService.ClientResponse(
                id, "Updated Name", null, null, null, null, null
        );

        when(clientService.update(eq(id), any(ClientService.ClientRequest.class))).thenReturn(mockResponse);

        mockMvc.perform(put("/api/clients/{id}", id)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated Name"));
    }

    @Test
    @WithMockUser
    @DisplayName("DELETE /api/clients/{id} should return 204")
    void delete_returns_204() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(delete("/api/clients/{id}", id).with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser
    @DisplayName("GET /api/clients/{id} should return 404 when not found")
    void find_by_id_returns_404() throws Exception {
        UUID id = UUID.randomUUID();
        when(clientService.findById(id)).thenThrow(new InvoiceAppException("Client not found", HttpStatus.NOT_FOUND));

        mockMvc.perform(get("/api/clients/{id}", id)).andExpect(status().isNotFound());
    }
}