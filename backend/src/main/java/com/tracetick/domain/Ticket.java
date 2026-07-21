package com.tracetick.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.Getter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Entity
@Table(name = "tickets")
@Getter
public class Ticket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Enumerated(EnumType.STRING)
    @Column(name = "origin", nullable = false, length = 16)
    private TicketOrigin origin;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reporter_user_id")
    private User reporter;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignee_user_id")
    private User assignee;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "description", nullable = false, columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 16)
    private Severity severity;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false, length = 16)
    private TicketState state;

    @Column(name = "fingerprint", length = 255)
    private String fingerprint;

    @Column(name = "refire_count", nullable = false)
    private int refireCount;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_payload", columnDefinition = "jsonb")
    private Map<String, Object> rawPayload;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "ticket_tags",
            joinColumns = @JoinColumn(name = "ticket_id"))
    private Set<Tag> tags = new HashSet<>();

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "ticket_watchers",
            joinColumns = @JoinColumn(name = "ticket_id"),
            inverseJoinColumns = @JoinColumn(name = "user_id"))
    private Set<User> watchers = new HashSet<>();

    @OneToMany(mappedBy = "ticket", cascade = CascadeType.ALL, orphanRemoval = true,
            fetch = FetchType.LAZY)
    @OrderBy("createdAt ASC, id ASC")
    private List<TicketEvent> events = new ArrayList<>();

    @Transient
    private final List<TicketEvent> newEvents = new ArrayList<>();

    protected Ticket() {
    }

    private Ticket(Customer customer, TicketOrigin origin, User reporter, User assignee,
                   String title, String description, Severity severity, TicketState state,
                   String fingerprint, int refireCount, Map<String, Object> rawPayload,
                   Instant createdAt, Set<Tag> tags) {
        this.customer = customer;
        this.origin = origin;
        this.reporter = reporter;
        this.assignee = assignee;
        this.title = title;
        this.description = description;
        this.severity = severity;
        this.state = state;
        this.fingerprint = fingerprint;
        this.refireCount = refireCount;
        this.rawPayload = rawPayload;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
        this.resolvedAt = null;
        this.closedAt = null;
        if (tags != null && !tags.isEmpty()) {
            this.tags.addAll(tags);
        }
    }

    public static Ticket createHuman(Customer customer, User reporter, String title,
                                     String description, Severity severity, List<Tag> tags) {
        if (severity == null) {
            severity = Severity.MEDIUM;
        }
        Instant now = Instant.now();
        Set<Tag> tagSet = tags == null ? Set.of() : new HashSet<>(tags);
        return new Ticket(customer, TicketOrigin.HUMAN, reporter, null,
                title, description, severity, TicketState.OPEN,
                null, 0, null, now, tagSet);
    }

    public void transitionTo(TicketState target, User actor) {
        TicketState from = this.state;
        if (from == TicketState.RESOLVED && target == TicketState.IN_PROGRESS) {
            ensureCanReopen(actor);
        }
        target = this.state.requireTransitionTo(target);
        this.state = target;
        this.updatedAt = Instant.now();
        if (target == TicketState.RESOLVED) {
            this.resolvedAt = this.updatedAt;
        }
        if (target == TicketState.CLOSED) {
            this.closedAt = this.updatedAt;
        }
        if (from == TicketState.RESOLVED && target == TicketState.IN_PROGRESS) {
            this.resolvedAt = null;
        }
        appendInternalEvent(EventType.STATE_CHANGE, actor, Map.of("from", from.name(), "to", target.name()));
    }

    public void changeSeverity(Severity newSeverity, User actor) {
        Severity from = this.severity;
        this.severity = newSeverity;
        this.updatedAt = Instant.now();
        appendInternalEvent(EventType.SEVERITY_CHANGE, actor,
                Map.of("from", from.name(), "to", newSeverity.name()));
    }

    public void assign(User technician, User actor) {
        Long previousId = this.assignee == null ? null : this.assignee.getId();
        this.assignee = technician;
        this.updatedAt = Instant.now();
        Map<String, Object> payload = new HashMap<>();
        payload.put("from_user_id", previousId);
        payload.put("to_user_id", technician == null ? null : technician.getId());
        appendInternalEvent(EventType.ASSIGNMENT_CHANGE, actor, payload);
    }

    public void unassign(User actor) {
        assign(null, actor);
    }

    public TicketEvent appendComment(User author, String body) {
        return appendInternalEvent(EventType.COMMENT, author, Map.of("body", body));
    }

    public boolean addTag(Tag tag, User actor) {
        if (!tags.add(tag)) {
            return false;
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("key", tag.getKey());
        payload.put("value", tag.getValue());
        payload.put("action", "added");
        appendInternalEvent(EventType.TAG_CHANGE, actor, payload);
        return true;
    }

    public boolean removeTag(String key, User actor) {
        Tag removed = tags.stream().filter(t -> t.getKey().equals(key)).findFirst().orElse(null);
        if (removed == null || !tags.remove(removed)) {
            return false;
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("key", removed.getKey());
        payload.put("value", removed.getValue());
        payload.put("action", "removed");
        appendInternalEvent(EventType.TAG_CHANGE, actor, payload);
        return true;
    }

    public boolean addWatcher(User watcher, User actor) {
        if (!watchers.add(watcher)) {
            return false;
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("user_id", watcher.getId());
        payload.put("action", "added");
        appendInternalEvent(EventType.WATCHER_CHANGE, actor, payload);
        return true;
    }

    public boolean removeWatcher(User watcher, User actor) {
        if (!watchers.remove(watcher)) {
            return false;
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("user_id", watcher.getId());
        payload.put("action", "removed");
        appendInternalEvent(EventType.WATCHER_CHANGE, actor, payload);
        return true;
    }

    public List<TicketEvent> getNewEvents() {
        return List.copyOf(newEvents);
    }

    public Set<Tag> getTags() {
        return Set.copyOf(tags);
    }

    public Set<User> getWatchers() {
        return Set.copyOf(watchers);
    }

    private void ensureCanReopen(User actor) {
        if (actor == null) {
            throw new InvalidTicketStateException(this.state, TicketState.IN_PROGRESS);
        }
        if (this.reporter == actor || this.assignee == actor) {
            return;
        }
        if (this.reporter != null && this.reporter.getEmail().equals(actor.getEmail())) {
            return;
        }
        if (this.assignee != null && this.assignee.getEmail().equals(actor.getEmail())) {
            return;
        }
        throw new InvalidTicketStateException(this.state, TicketState.IN_PROGRESS);
    }

    private TicketEvent appendInternalEvent(EventType type, User actor, Map<String, Object> payload) {
        TicketEvent event = TicketEvent.of(this, type, actor, payload);
        this.events.add(event);
        this.newEvents.add(event);
        return event;
    }
}
