package com.example.tickettriage.web.dto;

import com.example.tickettriage.domain.Severity;
import com.example.tickettriage.domain.Ticket;
import com.example.tickettriage.domain.TriageStatus;

import java.time.Instant;

/**
 * Outbound representation of a ticket, including its triage status and verdict.
 * {@code severity}, {@code suggestedQueue} and {@code rationale} are {@code null}
 * until triage completes.
 */
public record TicketResponse(
        Long id,
        String subject,
        String body,
        TriageStatus triageStatus,
        Severity severity,
        String suggestedQueue,
        String rationale,
        Instant createdAt
) {

    public static TicketResponse from(Ticket ticket) {
        return new TicketResponse(
                ticket.getId(),
                ticket.getSubject(),
                ticket.getBody(),
                ticket.getTriageStatus(),
                ticket.getSeverity(),
                ticket.getSuggestedQueue(),
                ticket.getRationale(),
                ticket.getCreatedAt()
        );
    }
}
