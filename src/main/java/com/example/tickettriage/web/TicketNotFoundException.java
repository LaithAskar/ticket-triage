package com.example.tickettriage.web;

/**
 * Thrown when a ticket lookup by id finds nothing. Mapped to HTTP 404 by
 * {@link GlobalExceptionHandler}.
 */
public class TicketNotFoundException extends RuntimeException {

    public TicketNotFoundException(Long id) {
        super("Ticket not found: " + id);
    }
}
