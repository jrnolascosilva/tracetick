package com.tracetick.api.dto;

import com.tracetick.domain.Ticket;
import com.tracetick.domain.TicketEvent;

import java.util.List;

public record TicketDetailDto(
        TicketDto ticket,
        List<EventDto> events,
        List<Long> watcherIds) {

    public static TicketDetailDto from(Ticket ticket, List<TicketEvent> events, List<Long> watcherIds) {
        return new TicketDetailDto(
                TicketDto.from(ticket),
                events.stream().map(EventDto::from).toList(),
                watcherIds);
    }
}
