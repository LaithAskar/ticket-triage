package com.example.tickettriage.web;

import com.example.tickettriage.domain.Severity;
import com.example.tickettriage.domain.Ticket;
import com.example.tickettriage.domain.TriageStatus;
import com.example.tickettriage.service.TicketService;
import com.example.tickettriage.web.dto.CreateTicketRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.data.web.SpringDataWebAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Controller-slice test. Security filters are disabled here so the focus stays
 * on request mapping, validation and status codes; security is covered
 * end-to-end in {@link com.example.tickettriage.ApiIntegrationTest}.
 */
@WebMvcTest(TicketController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(SpringDataWebAutoConfiguration.class)
class TicketControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private TicketService service;

    private Ticket sampleTicket(Long id, TriageStatus statusValue, Severity severity) {
        Ticket ticket = new Ticket("Login fails with 500", "All users get a 500 error when logging in.");
        ReflectionTestUtils.setField(ticket, "id", id);
        ReflectionTestUtils.setField(ticket, "createdAt", Instant.parse("2026-01-01T00:00:00Z"));
        ReflectionTestUtils.setField(ticket, "triageStatus", statusValue);
        ReflectionTestUtils.setField(ticket, "severity", severity);
        return ticket;
    }

    @Test
    void create_withValidPayload_returns202PendingWithLocation() throws Exception {
        when(service.create(any(CreateTicketRequest.class)))
                .thenReturn(sampleTicket(1L, TriageStatus.PENDING, null));
        CreateTicketRequest request = new CreateTicketRequest(
                "Login fails with 500", "All users get a 500 error when logging in.");

        mockMvc.perform(post("/api/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(header().string("Location", "http://localhost/api/tickets/1"))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.triageStatus").value("PENDING"));
    }

    @Test
    void create_withBlankSubject_returns400WithFieldErrors() throws Exception {
        String payload = "{\"subject\":\"\",\"body\":\"a valid body here\"}";

        mockMvc.perform(post("/api/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.fieldErrors.subject").exists());
    }

    @Test
    void getById_whenFound_returns200() throws Exception {
        when(service.getById(1L)).thenReturn(sampleTicket(1L, TriageStatus.COMPLETED, Severity.HIGH));

        mockMvc.perform(get("/api/tickets/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.severity").value("HIGH"))
                .andExpect(jsonPath("$.triageStatus").value("COMPLETED"));
    }

    @Test
    void getById_whenMissing_returns404() throws Exception {
        when(service.getById(99L)).thenThrow(new TicketNotFoundException(99L));

        mockMvc.perform(get("/api/tickets/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Ticket not found: 99"));
    }

    @Test
    void list_returnsPaginatedEnvelope() throws Exception {
        Page<Ticket> page = new PageImpl<>(
                List.of(sampleTicket(5L, TriageStatus.COMPLETED, Severity.CRITICAL)),
                PageRequest.of(0, 20), 1);
        when(service.list(eq(Severity.CRITICAL), any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/api/tickets").param("severity", "CRITICAL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(5))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.page").value(0));
    }

    @Test
    void list_withInvalidSeverity_returns400() throws Exception {
        mockMvc.perform(get("/api/tickets").param("severity", "BOGUS"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }
}
