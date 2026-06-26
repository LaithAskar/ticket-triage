package com.example.tickettriage.service;

import com.example.tickettriage.domain.Severity;
import com.example.tickettriage.domain.Ticket;
import com.example.tickettriage.domain.TriageStatus;
import com.example.tickettriage.repository.TicketRepository;
import com.example.tickettriage.web.TicketNotFoundException;
import com.example.tickettriage.web.dto.CreateTicketRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TicketServiceTest {

    @Mock
    private TicketRepository repository;

    @Mock
    private ApplicationEventPublisher events;

    @InjectMocks
    private TicketService service;

    @Test
    void create_persistsPendingTicket_andPublishesEvent() {
        CreateTicketRequest request = new CreateTicketRequest(
                "Production database is down",
                "Users cannot place orders, the primary DB is unreachable.");
        when(repository.save(any(Ticket.class))).thenAnswer(invocation -> {
            Ticket t = invocation.getArgument(0);
            ReflectionTestUtils.setField(t, "id", 7L);
            return t;
        });

        Ticket result = service.create(request);

        assertThat(result.getId()).isEqualTo(7L);
        assertThat(result.getTriageStatus()).isEqualTo(TriageStatus.PENDING);
        assertThat(result.getSeverity()).isNull();

        ArgumentCaptor<TicketCreatedEvent> event = ArgumentCaptor.forClass(TicketCreatedEvent.class);
        verify(events).publishEvent(event.capture());
        assertThat(event.getValue().ticketId()).isEqualTo(7L);
    }

    @Test
    void getById_whenPresent_returnsTicket() {
        Ticket ticket = new Ticket("subj", "body");
        when(repository.findById(42L)).thenReturn(Optional.of(ticket));

        assertThat(service.getById(42L)).isSameAs(ticket);
    }

    @Test
    void getById_whenMissing_throwsNotFound() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(99L))
                .isInstanceOf(TicketNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void list_withSeverity_usesFilteredQuery() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<Ticket> page = new PageImpl<>(List.of(new Ticket("s", "b")));
        when(repository.findBySeverity(Severity.HIGH, pageable)).thenReturn(page);

        assertThat(service.list(Severity.HIGH, pageable)).isSameAs(page);
        verify(repository).findBySeverity(Severity.HIGH, pageable);
    }

    @Test
    void list_withoutSeverity_returnsAll() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<Ticket> page = new PageImpl<>(List.of(new Ticket("s", "b")));
        when(repository.findAll(pageable)).thenReturn(page);

        assertThat(service.list(null, pageable)).isSameAs(page);
        verify(repository).findAll(pageable);
    }
}
