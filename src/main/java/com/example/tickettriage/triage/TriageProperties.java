package com.example.tickettriage.triage;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds the {@code triage.*} configuration block. Plain class with
 * getters/setters (no Lombok) so it can be relaxed-bound and mutated by the
 * environment.
 */
@ConfigurationProperties(prefix = "triage")
public class TriageProperties {

    /** Active triage strategy: {@code stub} or {@code llm}. */
    private String mode = "stub";

    /** Base URL of the Anthropic-style messages API. */
    private String apiUrl = "https://api.anthropic.com";

    /** API key sent as the {@code x-api-key} header (LLM mode only). */
    private String apiKey = "";

    /** Model identifier requested from the messages API. */
    private String model = "claude-haiku-4-5-20251001";

    /** Upper bound on tokens the model may generate. */
    private int maxTokens = 256;

    /** Value of the {@code anthropic-version} header. */
    private String anthropicVersion = "2023-06-01";

    /** Request timeout, in seconds, for the LLM call. */
    private int timeoutSeconds = 20;

    /** Number of retries for transient LLM failures (5xx / timeouts). */
    private int maxRetries = 2;

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public String getApiUrl() {
        return apiUrl;
    }

    public void setApiUrl(String apiUrl) {
        this.apiUrl = apiUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public int getMaxTokens() {
        return maxTokens;
    }

    public void setMaxTokens(int maxTokens) {
        this.maxTokens = maxTokens;
    }

    public String getAnthropicVersion() {
        return anthropicVersion;
    }

    public void setAnthropicVersion(String anthropicVersion) {
        this.anthropicVersion = anthropicVersion;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }
}
