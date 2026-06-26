package com.example.tickettriage.domain;

/**
 * Lifecycle of a ticket's triage. Tickets are accepted immediately
 * ({@code PENDING}) and classified asynchronously, ending in
 * {@code COMPLETED} or {@code FAILED}.
 */
public enum TriageStatus {
    PENDING,
    COMPLETED,
    FAILED
}
