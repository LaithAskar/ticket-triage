package com.example.tickettriage.repository;

import com.example.tickettriage.domain.Severity;
import com.example.tickettriage.domain.Ticket;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TicketRepository extends JpaRepository<Ticket, Long> {

    Page<Ticket> findBySeverity(Severity severity, Pageable pageable);
}
