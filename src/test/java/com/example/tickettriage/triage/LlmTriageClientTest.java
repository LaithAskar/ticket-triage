package com.example.tickettriage.triage;

import com.example.tickettriage.domain.Severity;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the WebClient-backed LLM client against a stubbed HTTP server:
 * response parsing, retry on transient 5xx, and graceful fallback. This proves
 * the previously untested LLM branch.
 */
class LlmTriageClientTest {

    private MockWebServer server;
    private LlmTriageClient client;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();

        TriageProperties props = new TriageProperties();
        props.setApiUrl(server.url("/").toString());
        props.setApiKey("test-key");
        props.setModel("claude-test");
        props.setTimeoutSeconds(5);
        props.setMaxRetries(2);

        client = new LlmTriageClient(WebClient.builder(), props);
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    private MockResponse json(String severityText) {
        return new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"content\":[{\"type\":\"text\",\"text\":\"" + severityText + "\"}]}");
    }

    @Test
    void parsesSeverityFromSuccessfulResponse() {
        server.enqueue(json("CRITICAL\\nDatabase is fully down."));

        TriageResult result = client.triage("DB down", "primary database unreachable");

        assertThat(result.severity()).isEqualTo(Severity.CRITICAL);
        assertThat(result.suggestedQueue()).isEqualTo("On-Call");
        assertThat(result.rationale()).contains("claude-test");
    }

    @Test
    void retriesOnTransientServerErrorThenSucceeds() throws InterruptedException {
        server.enqueue(new MockResponse().setResponseCode(503));
        server.enqueue(json("HIGH\\nElevated error rate."));

        TriageResult result = client.triage("errors", "spiking 5xx");

        assertThat(result.severity()).isEqualTo(Severity.HIGH);
        assertThat(server.getRequestCount()).isEqualTo(2);
    }

    @Test
    void fallsBackToMediumWhenServerKeepsFailing() {
        server.enqueue(new MockResponse().setResponseCode(500));
        server.enqueue(new MockResponse().setResponseCode(500));
        server.enqueue(new MockResponse().setResponseCode(500));

        TriageResult result = client.triage("anything", "still failing");

        assertThat(result.severity()).isEqualTo(Severity.MEDIUM);
        assertThat(result.rationale()).contains("unavailable");
    }

    @Test
    void doesNotRetryOnClientErrorAndFallsBack() {
        server.enqueue(new MockResponse().setResponseCode(400));

        TriageResult result = client.triage("bad", "request");

        assertThat(result.severity()).isEqualTo(Severity.MEDIUM);
        assertThat(server.getRequestCount()).isEqualTo(1);
    }
}
