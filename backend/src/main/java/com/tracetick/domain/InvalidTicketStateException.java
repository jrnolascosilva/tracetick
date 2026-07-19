package com.tracetick.domain;

public class InvalidTicketStateException extends RuntimeException {

    public InvalidTicketStateException(TicketState from, TicketState to) {
        super("Invalid Ticket state transition: " + from + " -> " + to);
    }
}
