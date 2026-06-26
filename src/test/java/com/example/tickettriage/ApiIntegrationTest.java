package com.example.tickettriage;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end test over the full application context (real H2 + Flyway, security,
 * async triage). Proves: API-key enforcement, the 202 → asynchronous-completion
 * flow, and that the offline classifier routes a real ticket correctly.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ApiIntegrationTest {

    private static final String API_KEY_HEADER = "X-API-Key";
    private static final String API_KEY = "local-dev-key";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private static final String BODY = """
            {"subject":"Production outage","body":"the production database is down, all users affected"}""";

    @Test
    void rejectsRequestsWithoutApiKey() throws Exception {
        mockMvc.perform(post("/api/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void createTriagesAsynchronouslyAndIsRetrievable() throws Exception {
        String response = mockMvc.perform(post("/api/tickets")
                        .header(API_KEY_HEADER, API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.triageStatus").value("PENDING"))
                .andReturn().getResponse().getContentAsString();

        long id = objectMapper.readTree(response).get("id").asLong();

        // Triage runs on a background thread; poll until it completes.
        await().atMost(5, TimeUnit.SECONDS).pollInterval(50, TimeUnit.MILLISECONDS)
                .until(() -> "COMPLETED".equals(triageStatusOf(id)));

        mockMvc.perform(get("/api/tickets/{id}", id).header(API_KEY_HEADER, API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.severity").value("CRITICAL"))
                .andExpect(jsonPath("$.suggestedQueue").value("Database"));
    }

    @Test
    void listsTicketsWithPaginationEnvelope() throws Exception {
        mockMvc.perform(post("/api/tickets")
                        .header(API_KEY_HEADER, API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isAccepted());

        mockMvc.perform(get("/api/tickets").header(API_KEY_HEADER, API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.size").value(20));
    }

    @Test
    void healthEndpointIsPublic() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    private String triageStatusOf(long id) throws Exception {
        String json = mockMvc.perform(get("/api/tickets/{id}", id).header(API_KEY_HEADER, API_KEY))
                .andReturn().getResponse().getContentAsString();
        JsonNode node = objectMapper.readTree(json);
        return node.path("triageStatus").asText();
    }
}
