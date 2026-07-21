package com.tracetick.domain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class TicketTest {

    @Test
    void createHumanTicketStoresFieldsAndDefaultsStateToOpen() {
        Customer customer = Customer.create("TraceTick", "ops@tracetick.local");
        User reporter = User.create(customer, "reporter@tracetick.local", "hash", Role.CUSTOMER);

        Ticket ticket = Ticket.createHuman(customer, reporter,
                "API down", "API returns 500", Severity.HIGH, List.of());

        assertThat(ticket.getCustomer()).isSameAs(customer);
        assertThat(ticket.getReporter()).isSameAs(reporter);
        assertThat(ticket.getTitle()).isEqualTo("API down");
        assertThat(ticket.getDescription()).isEqualTo("API returns 500");
        assertThat(ticket.getSeverity()).isEqualTo(Severity.HIGH);
        assertThat(ticket.getState()).isEqualTo(TicketState.OPEN);
        assertThat(ticket.getOrigin()).isEqualTo(TicketOrigin.HUMAN);
        assertThat(ticket.getAssignee()).isNull();
        assertThat(ticket.getRefireCount()).isZero();
        assertThat(ticket.getFingerprint()).isNull();
        assertThat(ticket.getRawPayload()).isNull();
        assertThat(ticket.getCreatedAt()).isNotNull();
        assertThat(ticket.getUpdatedAt()).isNotNull();
        assertThat(ticket.getResolvedAt()).isNull();
        assertThat(ticket.getClosedAt()).isNull();
    }

    @Test
    void createHumanTicketStoresTagsAtCreation() {
        Customer customer = Customer.create("TraceTick", "ops@tracetick.local");
        User reporter = User.create(customer, "reporter@tracetick.local", "hash", Role.CUSTOMER);
        Tag serviceApi = Tag.of("service", "api");
        Tag envProd = Tag.of("env", "prod");

        Ticket ticket = Ticket.createHuman(customer, reporter,
                "API down", "API returns 500", Severity.MEDIUM,
                List.of(serviceApi, envProd));

        assertThat(ticket.getTags())
                .extracting(Tag::getKey, Tag::getValue)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("service", "api"),
                        org.assertj.core.groups.Tuple.tuple("env", "prod"));
    }

    @Test
    void createHumanTicketDefaultsSeverityToMediumWhenNullIsProvided() {
        Customer customer = Customer.create("TraceTick", "ops@tracetick.local");
        User reporter = User.create(customer, "reporter@tracetick.local", "hash", Role.CUSTOMER);

        Ticket ticket = Ticket.createHuman(customer, reporter,
                "API down", "API returns 500", null, List.of());

        assertThat(ticket.getSeverity()).isEqualTo(Severity.MEDIUM);
    }

    @Test
    void transitionAdvancesStateAndStampsUpdatedAt() {
        Ticket ticket = newOpenTicket();

        ticket.transitionTo(TicketState.IN_PROGRESS, ticket.getReporter());

        assertThat(ticket.getState()).isEqualTo(TicketState.IN_PROGRESS);
        assertThat(ticket.getUpdatedAt()).isNotNull();
    }

    @Test
    void transitionToResolvedStampsResolvedAt() {
        Ticket ticket = newOpenTicket();
        ticket.transitionTo(TicketState.IN_PROGRESS, ticket.getReporter());

        ticket.transitionTo(TicketState.RESOLVED, ticket.getReporter());

        assertThat(ticket.getState()).isEqualTo(TicketState.RESOLVED);
        assertThat(ticket.getResolvedAt()).isNotNull();
    }

    @Test
    void transitionToClosedStampsClosedAt() {
        Ticket ticket = newOpenTicket();
        ticket.transitionTo(TicketState.IN_PROGRESS, ticket.getReporter());
        ticket.transitionTo(TicketState.RESOLVED, ticket.getReporter());

        ticket.transitionTo(TicketState.CLOSED, ticket.getReporter());

        assertThat(ticket.getState()).isEqualTo(TicketState.CLOSED);
        assertThat(ticket.getClosedAt()).isNotNull();
    }

    @Test
    void transitionToClosedFromInProgressStampsClosedAtAndLeavesResolvedAtNull() {
        Ticket ticket = newOpenTicket();
        ticket.transitionTo(TicketState.IN_PROGRESS, ticket.getReporter());

        ticket.transitionTo(TicketState.CLOSED, ticket.getReporter());

        assertThat(ticket.getClosedAt()).isNotNull();
        assertThat(ticket.getResolvedAt()).isNull();
    }

    @Test
    void transitionFromClosedAlwaysFails() {
        Ticket ticket = newOpenTicket();
        ticket.transitionTo(TicketState.IN_PROGRESS, ticket.getReporter());
        ticket.transitionTo(TicketState.RESOLVED, ticket.getReporter());
        ticket.transitionTo(TicketState.CLOSED, ticket.getReporter());

        for (TicketState target : TicketState.values()) {
            assertThatExceptionOfType(InvalidTicketStateException.class)
                    .as("CLOSED must reject transition to %s", target)
                    .isThrownBy(() -> ticket.transitionTo(target, ticket.getReporter()));
        }
    }

    @Test
    void reopenFromResolvedGoesBackToInProgressAndClearsResolvedAt() {
        Ticket ticket = newOpenTicket();
        ticket.transitionTo(TicketState.IN_PROGRESS, ticket.getReporter());
        ticket.transitionTo(TicketState.RESOLVED, ticket.getReporter());

        ticket.transitionTo(TicketState.IN_PROGRESS, ticket.getReporter());

        assertThat(ticket.getState()).isEqualTo(TicketState.IN_PROGRESS);
        assertThat(ticket.getResolvedAt()).as("resolvedAt is cleared on reopen").isNull();
    }

    @Test
    void reopenByReporterOrAssigneeSucceeds() {
        Ticket ticket = newOpenTicket();
        Customer customer = ticket.getCustomer();
        User tech = User.create(customer, "tech@tracetick.local", "h", Role.TECHNICIAN);
        ticket.transitionTo(TicketState.IN_PROGRESS, ticket.getReporter());
        ticket.assign(tech, ticket.getReporter());
        ticket.transitionTo(TicketState.RESOLVED, ticket.getReporter());

        ticket.transitionTo(TicketState.IN_PROGRESS, tech);

        assertThat(ticket.getState()).isEqualTo(TicketState.IN_PROGRESS);
    }

    @Test
    void reopenByNeitherReporterNorAssigneeFails() {
        Ticket ticket = newOpenTicket();
        Customer customer = ticket.getCustomer();
        User tech = User.create(customer, "tech@tracetick.local", "h", Role.TECHNICIAN);
        User stranger = User.create(customer, "stranger@tracetick.local", "h", Role.CUSTOMER);
        ticket.transitionTo(TicketState.IN_PROGRESS, ticket.getReporter());
        ticket.assign(tech, ticket.getReporter());
        ticket.transitionTo(TicketState.RESOLVED, ticket.getReporter());

        assertThatExceptionOfType(InvalidTicketStateException.class)
                .isThrownBy(() -> ticket.transitionTo(TicketState.IN_PROGRESS, stranger));
    }

    @Test
    void reopenByTechnicianWhoIsNotAssigneeStillFailsBecauseSpecRestrictsToReporterOrAssignee() {
        Ticket ticket = newOpenTicket();
        Customer customer = ticket.getCustomer();
        User tech1 = User.create(customer, "tech1@tracetick.local", "h", Role.TECHNICIAN);
        User tech2 = User.create(customer, "tech2@tracetick.local", "h", Role.TECHNICIAN);
        ticket.transitionTo(TicketState.IN_PROGRESS, ticket.getReporter());
        ticket.assign(tech1, ticket.getReporter());
        ticket.transitionTo(TicketState.RESOLVED, ticket.getReporter());

        assertThatExceptionOfType(InvalidTicketStateException.class)
                .as("Story 23 restricts reopen to reporter or assignee, not any technician")
                .isThrownBy(() -> ticket.transitionTo(TicketState.IN_PROGRESS, tech2));
    }

    @Test
    void changeSeverityUpdatesAndStampsUpdatedAt() {
        Ticket ticket = newOpenTicket();

        ticket.changeSeverity(Severity.CRITICAL, ticket.getReporter());

        assertThat(ticket.getSeverity()).isEqualTo(Severity.CRITICAL);
        assertThat(ticket.getUpdatedAt()).isNotNull();
    }

    @Test
    void assignStoresAssignee() {
        Customer customer = Customer.create("TraceTick", "ops@tracetick.local");
        User technician = User.create(customer, "tech@tracetick.local", "hash", Role.TECHNICIAN);

        Ticket ticket = newOpenTicket();

        ticket.assign(technician, ticket.getReporter());

        assertThat(ticket.getAssignee()).isSameAs(technician);
    }

    @Test
    void unassignClearsAssignee() {
        Customer customer = Customer.create("TraceTick", "ops@tracetick.local");
        User technician = User.create(customer, "tech@tracetick.local", "hash", Role.TECHNICIAN);
        Ticket ticket = newOpenTicket();
        ticket.assign(technician, ticket.getReporter());

        ticket.unassign(ticket.getReporter());

        assertThat(ticket.getAssignee()).isNull();
    }

    @Test
    void appendCommentAddsCommentEvent() {
        User reporter = newOpenTicket().getReporter();

        Ticket ticket = newOpenTicket();
        ticket.appendComment(reporter, "I cannot log in");

        assertThat(ticket.getNewEvents()).hasSize(1);
        TicketEvent event = ticket.getNewEvents().get(0);
        assertThat(event.getType()).isEqualTo(EventType.COMMENT);
        assertThat(event.getActor()).isSameAs(reporter);
        assertThat(event.getPayload()).containsEntry("body", "I cannot log in");
        assertThat(event.getTicket()).isSameAs(ticket);
    }

    @Test
    void appendStateChangeAddsEventWithFromAndToAndActor() {
        Ticket ticket = newOpenTicket();
        User actor = ticket.getReporter();

        ticket.transitionTo(TicketState.IN_PROGRESS, actor);

        assertThat(ticket.getNewEvents()).hasSize(1);
        TicketEvent event = ticket.getNewEvents().get(0);
        assertThat(event.getType()).isEqualTo(EventType.STATE_CHANGE);
        assertThat(event.getActor()).isSameAs(actor);
        assertThat(event.getPayload()).containsEntry("from", "OPEN").containsEntry("to", "IN_PROGRESS");
    }

    @Test
    void changeSeverityAppendsSeverityChangeEventWithActor() {
        Ticket ticket = newOpenTicket();

        ticket.changeSeverity(Severity.CRITICAL, ticket.getReporter());

        TicketEvent event = ticket.getNewEvents().stream()
                .filter(e -> e.getType() == EventType.SEVERITY_CHANGE)
                .findFirst().orElseThrow();
        assertThat(event.getActor()).isSameAs(ticket.getReporter());
        assertThat(event.getPayload()).containsEntry("from", "MEDIUM").containsEntry("to", "CRITICAL");
    }

    @Test
    void assignAppendsAssignmentChangeEventWithActor() {
        Customer customer = Customer.create("TraceTick", "ops@tracetick.local");
        User technician = User.create(customer, "tech@tracetick.local", "hash", Role.TECHNICIAN);

        Ticket ticket = newOpenTicket();
        ticket.assign(technician, ticket.getReporter());
        int beforeCount = ticket.getNewEvents().size();

        ticket.unassign(technician);

        TicketEvent event = ticket.getNewEvents().stream()
                .skip(beforeCount)
                .filter(e -> e.getType() == EventType.ASSIGNMENT_CHANGE)
                .findFirst().orElseThrow();
        assertThat(event.getActor()).isSameAs(technician);
        assertThat(event.getPayload()).containsEntry("from_user_id", null).containsEntry("to_user_id", null);
    }

    @Test
    void addTagAppendsTagChangeEventWithKeyAndValue() {
        Ticket ticket = newOpenTicket();
        Tag tag = Tag.of("service", "api");

        ticket.addTag(tag, ticket.getReporter());

        assertThat(ticket.getTags()).contains(tag);
        TicketEvent event = ticket.getNewEvents().stream()
                .filter(e -> e.getType() == EventType.TAG_CHANGE)
                .findFirst().orElseThrow();
        assertThat(event.getActor()).isSameAs(ticket.getReporter());
        assertThat(event.getPayload()).containsEntry("key", "service").containsEntry("value", "api").containsEntry("action", "added");
    }

    @Test
    void addTagIsIdempotentAndDoesNotAppendEventWhenAlreadyPresent() {
        Ticket ticket = newOpenTicket();
        Tag tag = Tag.of("service", "api");
        ticket.addTag(tag, ticket.getReporter());
        int countAfterFirst = ticket.getNewEvents().size();

        ticket.addTag(tag, ticket.getReporter());

        assertThat(ticket.getTags()).hasSize(1);
        assertThat(ticket.getNewEvents()).hasSize(countAfterFirst);
    }

    @Test
    void removeTagAppendsTagChangeEventWithActionRemoved() {
        Ticket ticket = newOpenTicket();
        Tag tag = Tag.of("service", "api");
        ticket.addTag(tag, ticket.getReporter());
        int beforeCount = ticket.getNewEvents().size();

        ticket.removeTag("service", ticket.getReporter());

        assertThat(ticket.getTags()).isEmpty();
        TicketEvent event = ticket.getNewEvents().stream()
                .skip(beforeCount)
                .filter(e -> e.getType() == EventType.TAG_CHANGE)
                .findFirst().orElseThrow();
        assertThat(event.getPayload()).containsEntry("key", "service").containsEntry("value", "api").containsEntry("action", "removed");
    }

    @Test
    void removeTagIsIdempotentAndDoesNotAppendEventWhenAbsent() {
        Ticket ticket = newOpenTicket();
        int beforeCount = ticket.getNewEvents().size();

        ticket.removeTag("missing", ticket.getReporter());

        assertThat(ticket.getNewEvents()).hasSize(beforeCount);
    }

    @Test
    void addWatcherAppendsWatcherChangeEventWithActionAdded() {
        Customer customer = Customer.create("TraceTick", "ops@tracetick.local");
        User watcher = User.create(customer, "watcher@tracetick.local", "hash", Role.CUSTOMER);

        Ticket ticket = newOpenTicket();

        ticket.addWatcher(watcher, ticket.getReporter());

        assertThat(ticket.getWatchers()).contains(watcher);
        TicketEvent event = ticket.getNewEvents().stream()
                .filter(e -> e.getType() == EventType.WATCHER_CHANGE)
                .findFirst().orElseThrow();
        assertThat(event.getActor()).isSameAs(ticket.getReporter());
        assertThat(event.getPayload()).containsEntry("user_id", null).containsEntry("action", "added");
    }

    @Test
    void addWatcherIsIdempotentAndDoesNotAppendEventWhenAlreadyPresent() {
        Customer customer = Customer.create("TraceTick", "ops@tracetick.local");
        User watcher = User.create(customer, "watcher@tracetick.local", "hash", Role.CUSTOMER);

        Ticket ticket = newOpenTicket();
        ticket.addWatcher(watcher, ticket.getReporter());
        int countAfterFirst = ticket.getNewEvents().size();

        ticket.addWatcher(watcher, ticket.getReporter());

        assertThat(ticket.getWatchers()).hasSize(1);
        assertThat(ticket.getNewEvents()).hasSize(countAfterFirst);
    }

    @Test
    void removeWatcherAppendsWatcherChangeEventWithActionRemoved() {
        Customer customer = Customer.create("TraceTick", "ops@tracetick.local");
        User watcher = User.create(customer, "watcher@tracetick.local", "hash", Role.CUSTOMER);

        Ticket ticket = newOpenTicket();
        ticket.addWatcher(watcher, ticket.getReporter());
        int beforeCount = ticket.getNewEvents().size();

        ticket.removeWatcher(watcher, ticket.getReporter());

        assertThat(ticket.getWatchers()).isEmpty();
        TicketEvent event = ticket.getNewEvents().stream()
                .skip(beforeCount)
                .filter(e -> e.getType() == EventType.WATCHER_CHANGE)
                .findFirst().orElseThrow();
        assertThat(event.getPayload()).containsEntry("action", "removed");
    }

    @Test
    void removeWatcherIsIdempotentAndDoesNotAppendEventWhenAbsent() {
        Customer customer = Customer.create("TraceTick", "ops@tracetick.local");
        User watcher = User.create(customer, "watcher@tracetick.local", "hash", Role.CUSTOMER);

        Ticket ticket = newOpenTicket();
        int beforeCount = ticket.getNewEvents().size();

        ticket.removeWatcher(watcher, ticket.getReporter());

        assertThat(ticket.getNewEvents()).hasSize(beforeCount);
    }

    private static Ticket newOpenTicket() {
        Customer customer = Customer.create("TraceTick", "ops@tracetick.local");
        User reporter = User.create(customer, "reporter@tracetick.local", "hash", Role.CUSTOMER);
        return Ticket.createHuman(customer, reporter,
                "API down", "API returns 500", Severity.MEDIUM, List.of());
    }
}
