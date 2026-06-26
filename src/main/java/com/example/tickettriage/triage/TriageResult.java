package com.example.tickettriage.triage;

import com.example.tickettriage.domain.Severity;

/**
 * Outcome of triaging a ticket: a severity plus the queue it should be routed
 * to and a short human-readable rationale.
 */
public record TriageResult(
        Severity severity,
        String suggestedQueue,
        String rationale
) {
}
