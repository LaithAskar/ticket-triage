package com.example.tickettriage.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Inbound payload for creating a ticket. Validated via {@code @Valid} in the
 * controller; kept separate from the {@code Ticket} entity so the persistence
 * model and the API contract can evolve independently.
 */
public record CreateTicketRequest(

        @NotBlank(message = "subject must not be blank")
        @Size(max = 200, message = "subject must be at most 200 characters")
        String subject,

        @NotBlank(message = "body must not be blank")
        @Size(min = 5, max = 10_000, message = "body must be between 5 and 10000 characters")
        String body
) {
}
