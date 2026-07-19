package com.tracetick.domain;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public enum TicketState {

    OPEN,
    IN_PROGRESS,
    RESOLVED,
    CLOSED;

    private static final Map<TicketState, Set<TicketState>> ALLOWED = Map.of(
            OPEN, EnumSet.of(IN_PROGRESS),
            IN_PROGRESS, EnumSet.of(RESOLVED, CLOSED),
            RESOLVED, EnumSet.of(IN_PROGRESS, CLOSED),
            CLOSED, EnumSet.noneOf(TicketState.class));

    public boolean canTransitionTo(TicketState target) {
        return ALLOWED.get(this).contains(target);
    }

    public TicketState requireTransitionTo(TicketState target) {
        if (!canTransitionTo(target)) {
            throw new InvalidTicketStateException(this, target);
        }
        return target;
    }
}
