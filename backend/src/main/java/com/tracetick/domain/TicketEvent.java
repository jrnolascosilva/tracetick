package com.tracetick.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Entity
@Table(name = "events")
@Getter
public class TicketEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ticket_id", nullable = false)
    private Ticket ticket;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 32)
    private EventType type;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_user_id")
    private User actor;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> payload = new HashMap<>();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected TicketEvent() {
    }

    private TicketEvent(Ticket ticket, EventType type, User actor, Map<String, Object> payload, Instant createdAt) {
        this.ticket = ticket;
        this.type = type;
        this.actor = actor;
        this.payload = payload;
        this.createdAt = createdAt;
    }

    public static TicketEvent of(Ticket ticket, EventType type, User actor, Map<String, Object> payload) {
        return new TicketEvent(ticket, type, actor, payload == null ? Map.of() : payload, Instant.now());
    }
}
