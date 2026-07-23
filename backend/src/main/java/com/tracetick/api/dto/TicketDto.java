package com.tracetick.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.tracetick.domain.Severity;
import com.tracetick.domain.Tag;
import com.tracetick.domain.Ticket;
import com.tracetick.domain.TicketOrigin;
import com.tracetick.domain.TicketState;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * {@code fingerprint}, {@code rawPayload}, and {@code ingestionConfigurationId} are
 * non-null only on WEBHOOK-origin tickets (HUMAN-origin tickets carry no payload and no
 * source configuration). All three are omitted from JSON via {@link Include#NON_NULL} so
 * list views of mixed-origin tickets stay compact.
 */
@JsonInclude(Include.NON_NULL)
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
        String fingerprint,
        Long ingestionConfigurationId,
        Map<String, Object> rawPayload,
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
                ticket.getFingerprint(),
                ticket.getIngestionConfiguration() == null
                        ? null
                        : ticket.getIngestionConfiguration().getId(),
                ticket.getRawPayload() == null || ticket.getRawPayload().isEmpty()
                        ? null
                        : ticket.getRawPayload(),
                ticket.getCreatedAt(),
                ticket.getUpdatedAt(),
                ticket.getResolvedAt(),
                ticket.getClosedAt(),
                tags.stream().map(t -> new TagDto(t.getKey(), t.getValue())).toList());
    }
}
