package com.example.tickettriage.web;

import com.example.tickettriage.domain.Severity;
import com.example.tickettriage.domain.Ticket;
import com.example.tickettriage.service.TicketService;
import com.example.tickettriage.web.dto.CreateTicketRequest;
import com.example.tickettriage.web.dto.PageResponse;
import com.example.tickettriage.web.dto.TicketResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

@RestController
@RequestMapping("/api/tickets")
@Tag(name = "Tickets", description = "Create and triage IT incident tickets")
public class TicketController {

    private final TicketService service;

    public TicketController(TicketService service) {
        this.service = service;
    }

    @PostMapping
    @Operation(summary = "Create a ticket",
            description = "Accepts the ticket immediately (202) and triages it asynchronously.")
    public ResponseEntity<TicketResponse> create(@Valid @RequestBody CreateTicketRequest request,
                                                 UriComponentsBuilder uriBuilder) {
        Ticket ticket = service.create(request);
        URI location = uriBuilder.path("/api/tickets/{id}")
                .buildAndExpand(ticket.getId())
                .toUri();
        // 202 Accepted: triage is still pending when we respond.
        return ResponseEntity.accepted().location(location).body(TicketResponse.from(ticket));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a ticket by id")
    public TicketResponse getById(@PathVariable Long id) {
        return TicketResponse.from(service.getById(id));
    }

    @GetMapping
    @Operation(summary = "List tickets",
            description = "Paginated; optionally filtered by severity.")
    public PageResponse<TicketResponse> list(
            @RequestParam(required = false) Severity severity,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return PageResponse.of(service.list(severity, pageable), TicketResponse::from);
    }
}
