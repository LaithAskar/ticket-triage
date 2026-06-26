package com.example.tickettriage.service;

/**
 * Published after a ticket is persisted (and its transaction commits) so that
 * triage can run asynchronously without blocking the request thread.
 */
public record TicketCreatedEvent(Long ticketId) {
}
