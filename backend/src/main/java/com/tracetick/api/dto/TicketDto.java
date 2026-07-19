package com.tracetick.api.dto;

import com.tracetick.domain.Severity;
import com.tracetick.domain.Tag;
import com.tracetick.domain.Ticket;
import com.tracetick.domain.TicketOrigin;
import com.tracetick.domain.TicketState;

import java.time.Instant;
import java.util.List;
import java.util.Set;

public record TicketDto(
        Long id,
        TicketOrigin origin,
        Long reporterUserId,
        Long assigneeUserId,
        String title,
        String description,
        Severity severity,
        TicketState state,
        int refireCount,
        Instant createdAt,
        Instant updatedAt,
        Instant resolvedAt,
        Instant closedAt,
        List<TagDto> tags) {

    public static TicketDto from(Ticket ticket) {
        Set<Tag> tags = ticket.getTags();
        return new TicketDto(
                ticket.getId(),
                ticket.getOrigin(),
                ticket.getReporter() == null ? null : ticket.getReporter().getId(),
                ticket.getAssignee() == null ? null : ticket.getAssignee().getId(),
                ticket.getTitle(),
                ticket.getDescription(),
                ticket.getSeverity(),
                ticket.getState(),
                ticket.getRefireCount(),
                ticket.getCreatedAt(),
                ticket.getUpdatedAt(),
                ticket.getResolvedAt(),
                ticket.getClosedAt(),
                tags.stream().map(t -> new TagDto(t.getKey(), t.getValue())).toList());
    }
}
