package com.example.tickettriage.service;

import com.example.tickettriage.domain.Severity;
import com.example.tickettriage.domain.Ticket;
import com.example.tickettriage.domain.TriageStatus;
import com.example.tickettriage.repository.TicketRepository;
import com.example.tickettriage.triage.TriageClient;
import com.example.tickettriage.triage.TriageResult;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TriageEventListenerTest {

    @Mock
    private TicketRepository repository;

    @Mock
    private TriageClient triageClient;

    private SimpleMeterRegistry meterRegistry;
    private TriageEventListener listener;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        listener = new TriageEventListener(repository, triageClient, meterRegistry);
    }

    private Ticket pendingTicket(long id) {
        Ticket ticket = new Ticket("Login fails", "all users see a 500");
        ReflectionTestUtils.setField(ticket, "id", id);
        return ticket;
    }

    @Test
    void appliesTriageAndCompletesOnSuccess() {
        Ticket ticket = pendingTicket(1L);
        when(repository.findById(1L)).thenReturn(Optional.of(ticket));
        when(triageClient.triage(ticket.getSubject(), ticket.getBody()))
                .thenReturn(new TriageResult(Severity.HIGH, "General-Support", "rule-based"));

        listener.onTicketCreated(new TicketCreatedEvent(1L));

        assertThat(ticket.getTriageStatus()).isEqualTo(TriageStatus.COMPLETED);
        assertThat(ticket.getSeverity()).isEqualTo(Severity.HIGH);
        assertThat(ticket.getSuggestedQueue()).isEqualTo("General-Support");
        verify(repository).save(ticket);
        assertThat(meterRegistry.counter("tickets.triaged", "status", "COMPLETED", "severity", "HIGH")
                .count()).isEqualTo(1.0);
    }

    @Test
    void marksFailedWhenTriageThrows() {
        Ticket ticket = pendingTicket(2L);
        when(repository.findById(2L)).thenReturn(Optional.of(ticket));
        when(triageClient.triage(any(), any())).thenThrow(new RuntimeException("boom"));

        listener.onTicketCreated(new TicketCreatedEvent(2L));

        assertThat(ticket.getTriageStatus()).isEqualTo(TriageStatus.FAILED);
        assertThat(ticket.getRationale()).contains("Triage failed");
        verify(repository).save(ticket);
        assertThat(meterRegistry.counter("tickets.triaged", "status", "FAILED", "severity", "NONE")
                .count()).isEqualTo(1.0);
    }

    @Test
    void doesNothingWhenTicketMissing() {
        when(repository.findById(3L)).thenReturn(Optional.empty());

        listener.onTicketCreated(new TicketCreatedEvent(3L));

        verify(repository, never()).save(any());
    }
}
