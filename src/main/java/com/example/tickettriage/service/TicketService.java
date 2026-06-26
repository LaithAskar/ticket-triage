package com.example.tickettriage.service;

import com.example.tickettriage.domain.Severity;
import com.example.tickettriage.domain.Ticket;
import com.example.tickettriage.repository.TicketRepository;
import com.example.tickettriage.web.TicketNotFoundException;
import com.example.tickettriage.web.dto.CreateTicketRequest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service: accepts tickets and exposes reads. Triage itself runs
 * asynchronously — {@link #create} persists the ticket as {@code PENDING} and
 * publishes a {@link TicketCreatedEvent}; {@link TriageEventListener} performs
 * the classification after the transaction commits.
 */
@Service
@Transactional
public class TicketService {

    private final TicketRepository repository;
    private final ApplicationEventPublisher events;

    public TicketService(TicketRepository repository, ApplicationEventPublisher events) {
        this.repository = repository;
        this.events = events;
    }

    /**
     * Accept a new ticket. Returns immediately with the persisted, not-yet-triaged
     * ticket; triage is performed asynchronously.
     */
    public Ticket create(CreateTicketRequest request) {
        Ticket ticket = repository.save(new Ticket(request.subject(), request.body()));
        events.publishEvent(new TicketCreatedEvent(ticket.getId()));
        return ticket;
    }

    @Transactional(readOnly = true)
    public Ticket getById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new TicketNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public Page<Ticket> list(Severity severity, Pageable pageable) {
        return severity == null
                ? repository.findAll(pageable)
                : repository.findBySeverity(severity, pageable);
    }
}
