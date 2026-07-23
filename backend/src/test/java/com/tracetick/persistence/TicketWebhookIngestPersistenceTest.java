package com.tracetick.persistence;

import com.tracetick.domain.Customer;
import com.tracetick.domain.IngestionConfiguration;
import com.tracetick.domain.Severity;
import com.tracetick.domain.Ticket;
import com.tracetick.domain.TicketOrigin;
import com.tracetick.domain.TicketState;
import com.tracetick.domain.User;
import com.tracetick.domain.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class TicketWebhookIngestPersistenceTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("tracetick")
            .withUsername("tracetick")
            .withPassword("tracetick");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private IngestionConfigurationRepository ingestionConfigurationRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void webhookTicketPersistsIngestionConfigurationReference() {
        Customer customer = entityManager.persistAndFlush(
                Customer.create("TraceTick", "ops@tracetick.local"));
        IngestionConfiguration config = entityManager.persistAndFlush(
                IngestionConfiguration.create("PagerDuty", "tok-pd-1", "secret-pd-1",
                        Severity.MEDIUM, null, Map.of()));

        Ticket ticket = Ticket.createWebhook(customer, null, config,
                "Alert from PagerDuty", "Alert ingested from PagerDuty. See raw_payload for details.",
                Severity.MEDIUM, config.getId() + ":abc-123",
                Map.of("alert_id", "abc-123"), java.util.List.of());

        Ticket saved = ticketRepository.saveAndFlush(ticket);
        entityManager.clear();

        Ticket reloaded = ticketRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getOrigin()).isEqualTo(TicketOrigin.WEBHOOK);
        assertThat(reloaded.getIngestionConfiguration()).isNotNull();
        assertThat(reloaded.getIngestionConfiguration().getId()).isEqualTo(config.getId());
        assertThat(reloaded.getIngestionConfiguration().getName()).isEqualTo("PagerDuty");
        assertThat(reloaded.getFingerprint()).isEqualTo(config.getId() + ":abc-123");
    }

    @Test
    void humanTicketHasNullIngestionConfiguration() {
        Customer customer = entityManager.persistAndFlush(
                Customer.create("TraceTick", "ops@tracetick.local"));
        User reporter = entityManager.persistAndFlush(
                User.create(customer, "alice@tracetick.local", "h", Role.CUSTOMER));

        Ticket ticket = Ticket.createHuman(customer, reporter,
                "API down", "API returns 500", Severity.HIGH, java.util.List.of());
        Ticket saved = ticketRepository.saveAndFlush(ticket);
        entityManager.clear();

        Ticket reloaded = ticketRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getIngestionConfiguration()).isNull();
        assertThat(reloaded.getFingerprint()).isNull();
    }

    @Test
    void findFirstByFingerprintAndStateInReturnsActiveTicketsAndSkipsClosed() {
        Customer customer = entityManager.persistAndFlush(
                Customer.create("TraceTick", "ops@tracetick.local"));
        IngestionConfiguration config = entityManager.persistAndFlush(
                IngestionConfiguration.create("PagerDuty", "tok-fp", "secret-fp",
                        Severity.MEDIUM, null, Map.of()));

        Ticket active = ticketRepository.saveAndFlush(Ticket.createWebhook(customer, null, config,
                "Open", "d", Severity.MEDIUM, "fp:abc",
                Map.of(), java.util.List.of()));
        Ticket closed = ticketRepository.saveAndFlush(Ticket.createWebhook(customer, null, config,
                "Closed", "d", Severity.MEDIUM, "fp:abc",
                Map.of(), java.util.List.of()));
        closed.transitionTo(TicketState.IN_PROGRESS, null);
        closed.transitionTo(TicketState.RESOLVED, null);
        closed.transitionTo(TicketState.CLOSED, null);
        ticketRepository.saveAndFlush(closed);
        entityManager.clear();

        Optional<Ticket> match = ticketRepository.findFirstByFingerprintAndStateIn(
                "fp:abc", java.util.Set.of(
                        TicketState.OPEN, TicketState.IN_PROGRESS, TicketState.RESOLVED));

        assertThat(match).isPresent();
        assertThat(match.get().getId()).isEqualTo(active.getId());
    }
}
