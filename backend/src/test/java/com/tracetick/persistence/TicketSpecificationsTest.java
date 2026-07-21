package com.tracetick.persistence;

import com.tracetick.domain.Customer;
import com.tracetick.domain.Role;
import com.tracetick.domain.Severity;
import com.tracetick.domain.Tag;
import com.tracetick.domain.Ticket;
import com.tracetick.domain.TicketOrigin;
import com.tracetick.domain.TicketState;
import com.tracetick.domain.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class TicketSpecificationsTest {

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
    private TestEntityManager entityManager;

    private Customer customer;
    private User tech;
    private User alice;
    private User bob;

    @BeforeEach
    void setUp() {
        customer = entityManager.persistAndFlush(Customer.create("TraceTick", "ops@tracetick.local"));
        tech = entityManager.persistAndFlush(User.create(customer, "tech@tracetick.local", "h", Role.TECHNICIAN));
        alice = entityManager.persistAndFlush(User.create(customer, "alice@tracetick.local", "h", Role.CUSTOMER));
        bob = entityManager.persistAndFlush(User.create(customer, "bob@tracetick.local", "h", Role.CUSTOMER));

        ticketRepository.saveAndFlush(Ticket.createHuman(customer, alice,
                "API down", "Service X is returning 500", Severity.HIGH,
                List.of(Tag.of("service", "api"), Tag.of("env", "prod"))));
        ticketRepository.saveAndFlush(Ticket.createHuman(customer, bob,
                "Login broken", "Cannot log in after password reset", Severity.CRITICAL,
                List.of(Tag.of("service", "auth"), Tag.of("env", "prod"))));
        ticketRepository.saveAndFlush(Ticket.createHuman(customer, alice,
                "Slow dashboard", "Dashboard loads very slowly", Severity.LOW,
                List.of(Tag.of("service", "web"))));
        entityManager.clear();
    }

    @Test
    void findsAllTicketsForTechnician() {
        Page<Ticket> page = ticketRepository.findAll(
                TicketSpecifications.visibleTo(tech),
                PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt")));

        assertThat(page.getTotalElements()).isEqualTo(3);
    }

    @Test
    void restrictsToReporterForCustomer() {
        Page<Ticket> page = ticketRepository.findAll(
                TicketSpecifications.visibleTo(alice),
                PageRequest.of(0, 20));

        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent()).allMatch(t -> t.getReporter().getId().equals(alice.getId()));
    }

    @Test
    void customerCanAlsoSeeTicketsTheyWatch() {
        Ticket bobsTicket = ticketRepository.findAll().stream()
                .filter(t -> t.getReporter().getId().equals(bob.getId()))
                .findFirst().orElseThrow();
        bobsTicket.addWatcher(alice, bob);
        ticketRepository.saveAndFlush(bobsTicket);
        entityManager.clear();

        Page<Ticket> page = ticketRepository.findAll(
                TicketSpecifications.visibleTo(alice),
                PageRequest.of(0, 20));

        assertThat(page.getTotalElements())
                .as("alice's own 2 + the one she watches (bob's) = 3")
                .isEqualTo(3);
    }

    @Test
    void filtersByState() {
        Ticket open = ticketRepository.findAll().get(0);
        open.transitionTo(TicketState.IN_PROGRESS, open.getReporter());
        ticketRepository.saveAndFlush(open);
        entityManager.clear();

        Page<Ticket> openPage = ticketRepository.findAll(
                TicketSpecifications.hasState(TicketState.OPEN),
                PageRequest.of(0, 20));

        assertThat(openPage.getTotalElements()).isEqualTo(2);

        Page<Ticket> inProgressPage = ticketRepository.findAll(
                TicketSpecifications.hasState(TicketState.IN_PROGRESS),
                PageRequest.of(0, 20));

        assertThat(inProgressPage.getTotalElements()).isEqualTo(1);
    }

    @Test
    void filtersBySeverity() {
        Page<Ticket> critical = ticketRepository.findAll(
                TicketSpecifications.hasSeverity(Severity.CRITICAL),
                PageRequest.of(0, 20));

        assertThat(critical.getTotalElements()).isEqualTo(1);
        assertThat(critical.getContent().get(0).getTitle()).isEqualTo("Login broken");
    }

    @Test
    void filtersByTagKeyValue() {
        Page<Ticket> apiProd = ticketRepository.findAll(
                TicketSpecifications.hasTag("env", "prod"),
                PageRequest.of(0, 20));

        assertThat(apiProd.getTotalElements()).isEqualTo(2);
    }

    @Test
    void searchFreeTextFindsAcrossTitleAndDescription() {
        Page<Ticket> password = ticketRepository.findAll(
                TicketSpecifications.searchFreeText("password"),
                PageRequest.of(0, 20));

        assertThat(password.getTotalElements()).isEqualTo(1);
        assertThat(password.getContent().get(0).getTitle()).isEqualTo("Login broken");
    }

    @Test
    void searchFreeTextIsCaseInsensitive() {
        Page<Ticket> dashboard = ticketRepository.findAll(
                TicketSpecifications.searchFreeText("DASHBOARD"),
                PageRequest.of(0, 20));

        assertThat(dashboard.getTotalElements()).isEqualTo(1);
    }
}
