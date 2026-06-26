package com.example.tickettriage.triage;

/**
 * Strategy for classifying an incident ticket. Implementations are selected at
 * runtime by the {@code triage.mode} property:
 * <ul>
 *     <li>{@code stub} (default) &rarr; {@link StubTriageClient}</li>
 *     <li>{@code llm} &rarr; {@link LlmTriageClient}</li>
 * </ul>
 */
public interface TriageClient {

    /**
     * Classify a ticket from its subject and body.
     *
     * @param subject short summary of the incident
     * @param body    full description of the incident
     * @return the triage verdict (never {@code null})
     */
    TriageResult triage(String subject, String body);
}
