package com.example.tickettriage.triage;

import com.example.tickettriage.domain.Severity;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeoutException;

/**
 * Triage backed by an Anthropic-style {@code /v1/messages} endpoint, called via
 * {@link WebClient}. Active only when {@code triage.mode=llm}.
 *
 * <p>Resilience is built in: each call has a timeout and a bounded exponential
 * backoff retry for transient failures (5xx / timeouts). If the call ultimately
 * fails, triage degrades gracefully to a conservative {@code MEDIUM} verdict
 * rather than failing the ticket outright.
 */
@Component
@ConditionalOnProperty(name = "triage.mode", havingValue = "llm")
public class LlmTriageClient implements TriageClient {

    private static final Logger log = LoggerFactory.getLogger(LlmTriageClient.class);

    private static final String SYSTEM_PROMPT = """
            You are an IT incident triage assistant. Classify the ticket's severity as exactly one of:
            LOW, MEDIUM, HIGH, CRITICAL.
            Reply with ONLY the single severity word on the first line, then a one-sentence rationale.""";

    private final WebClient webClient;
    private final TriageProperties properties;

    public LlmTriageClient(WebClient.Builder webClientBuilder, TriageProperties properties) {
        this.properties = properties;
        this.webClient = webClientBuilder
                .baseUrl(properties.getApiUrl())
                .defaultHeader("x-api-key", properties.getApiKey())
                .defaultHeader("anthropic-version", properties.getAnthropicVersion())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Override
    public TriageResult triage(String subject, String body) {
        try {
            JsonNode response = webClient.post()
                    .uri("/v1/messages")
                    .bodyValue(requestBody(subject, body))
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .timeout(Duration.ofSeconds(properties.getTimeoutSeconds()))
                    .retryWhen(Retry.backoff(properties.getMaxRetries(), Duration.ofMillis(200))
                            .filter(this::isTransient))
                    .block();

            String text = extractText(response);
            Severity severity = parseSeverity(text);
            return new TriageResult(severity, queueFor(severity),
                    "LLM triage (" + properties.getModel() + "): " + summarize(text));
        } catch (Exception ex) {
            log.warn("LLM triage failed after retries, defaulting to MEDIUM: {}", ex.toString());
            return new TriageResult(
                    Severity.MEDIUM,
                    queueFor(Severity.MEDIUM),
                    "LLM triage unavailable (" + ex.getClass().getSimpleName() + "); defaulted to MEDIUM.");
        }
    }

    private Map<String, Object> requestBody(String subject, String body) {
        return Map.of(
                "model", properties.getModel(),
                "max_tokens", properties.getMaxTokens(),
                "system", SYSTEM_PROMPT,
                "messages", List.of(Map.of(
                        "role", "user",
                        "content", "Subject: " + subject + "\n\nBody: " + body)));
    }

    /** Retry on timeouts, connection errors and 5xx; never on 4xx (client errors). */
    private boolean isTransient(Throwable t) {
        if (t instanceof WebClientResponseException wcre) {
            return wcre.getStatusCode().is5xxServerError();
        }
        return t instanceof TimeoutException || t instanceof IOException;
    }

    /** Pull the text out of the Anthropic messages response: {@code content[0].text}. */
    private String extractText(JsonNode response) {
        if (response == null) {
            return "";
        }
        JsonNode content = response.path("content");
        if (content.isArray() && !content.isEmpty()) {
            return content.get(0).path("text").asText("");
        }
        return "";
    }

    /** Find the first severity keyword mentioned in the model's reply. */
    private Severity parseSeverity(String text) {
        String upper = text == null ? "" : text.toUpperCase(Locale.ROOT);
        int best = Integer.MAX_VALUE;
        Severity found = null;
        for (Severity s : Severity.values()) {
            int idx = upper.indexOf(s.name());
            if (idx >= 0 && idx < best) {
                best = idx;
                found = s;
            }
        }
        return found != null ? found : Severity.MEDIUM;
    }

    private String summarize(String text) {
        if (text == null || text.isBlank()) {
            return "no rationale returned";
        }
        String trimmed = text.strip();
        return trimmed.length() > 300 ? trimmed.substring(0, 300) + "..." : trimmed;
    }

    private String queueFor(Severity severity) {
        return severity == Severity.CRITICAL ? "On-Call" : "General-Support";
    }
}
