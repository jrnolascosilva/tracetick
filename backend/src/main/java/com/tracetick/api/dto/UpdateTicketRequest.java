package com.tracetick.api.dto;

import com.tracetick.domain.Severity;
import com.tracetick.domain.TicketState;

public record UpdateTicketRequest(
        TicketState state,
        Severity severity,
        Long assigneeUserId,
        Boolean unassign) {
}
