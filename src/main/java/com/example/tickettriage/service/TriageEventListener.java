package com.example.tickettriage.service;

import com.example.tickettriage.domain.Ticket;
import com.example.tickettriage.domain.TriageStatus;
import com.example.tickettriage.repository.TicketRepository;
import com.example.tickettriage.triage.TriageClient;
import com.example.tickettriage.triage.TriageResult;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Runs triage asynchronously, after the ticket-creating transaction commits.
 *
 * <p>Using {@link TransactionalEventListener} with {@link TransactionPhase#AFTER_COMMIT}
 * (rather than dispatching from inside the service) guarantees the row is
 * visible before the async thread loads it, avoiding a read-before-commit race.
 */
@Component
public class TriageEventListener {

    private static final Logger log = LoggerFactory.getLogger(TriageEventListener.class);

    private final TicketRepository repository;
    private final TriageClient triageClient;
    private final MeterRegistry meterRegistry;

    public TriageEventListener(TicketRepository repository,
                               TriageClient triageClient,
                               MeterRegistry meterRegistry) {
        this.repository = repository;
        this.triageClient = triageClient;
        this.meterRegistry = meterRegistry;
    }

    @Async("triageExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onTicketCreated(TicketCreatedEvent event) {
        Ticket ticket = repository.findById(event.ticketId()).orElse(null);
        if (ticket == null) {
            log.warn("Ticket {} vanished before triage", event.ticketId());
            return;
        }

        try {
            TriageResult result = triageClient.triage(ticket.getSubject(), ticket.getBody());
            ticket.applyTriage(result);
            repository.save(ticket);
            record(TriageStatus.COMPLETED, result.severity().name());
            log.info("Triaged ticket {} as {} -> {}", ticket.getId(), result.severity(), result.suggestedQueue());
        } catch (Exception ex) {
            ticket.markTriageFailed("Triage failed: " + ex.getMessage());
            repository.save(ticket);
            record(TriageStatus.FAILED, "NONE");
            log.error("Triage failed for ticket {}", ticket.getId(), ex);
        }
    }

    private void record(TriageStatus status, String severity) {
        meterRegistry.counter("tickets.triaged",
                "status", status.name(),
                "severity", severity).increment();
    }
}
