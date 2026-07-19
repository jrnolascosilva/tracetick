package com.tracetick.api.dto;

import com.tracetick.domain.EventType;
import com.tracetick.domain.TicketEvent;

import java.time.Instant;
import java.util.Map;

public record EventDto(
        Long id,
        EventType type,
        Long actorUserId,
        Map<String, Object> payload,
        Instant createdAt) {

    public static EventDto from(TicketEvent event) {
        return new EventDto(
                event.getId(),
                event.getType(),
                event.getActor() == null ? null : event.getActor().getId(),
                event.getPayload(),
                event.getCreatedAt());
    }
}
