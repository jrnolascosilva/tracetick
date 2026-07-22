package com.tracetick.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tracetick.domain.Customer;
import com.tracetick.domain.Role;
import com.tracetick.domain.Severity;
import com.tracetick.domain.Ticket;
import com.tracetick.domain.TicketEvent;
import com.tracetick.domain.TicketState;
import com.tracetick.domain.User;
import com.tracetick.persistence.CustomerRepository;
import com.tracetick.persistence.TicketEventRepository;
import com.tracetick.persistence.TicketRepository;
import com.tracetick.persistence.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
class TicketsControllerTest {

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
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private TicketEventRepository ticketEventRepository;

    private User alice;
    private User bob;
    private User tech;
    private MockHttpSession aliceSession;
    private MockHttpSession bobSession;
    private MockHttpSession techSession;

    @BeforeEach
    void setUp() throws Exception {
        ticketEventRepository.deleteAllInBatch();
        ticketRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
        customerRepository.deleteAllInBatch();

        Customer customer = customerRepository.saveAndFlush(Customer.create("TraceTick", "ops@tracetick.local"));
        alice = userRepository.saveAndFlush(User.create(customer, "alice@tracetick.local", passwordEncoder.encode("pw"), Role.CUSTOMER));
        bob = userRepository.saveAndFlush(User.create(customer, "bob@tracetick.local", passwordEncoder.encode("pw"), Role.CUSTOMER));
        tech = userRepository.saveAndFlush(User.create(customer, "tech@tracetick.local", passwordEncoder.encode("pw"), Role.TECHNICIAN));

        aliceSession = loginAs("alice@tracetick.local");
        bobSession = loginAs("bob@tracetick.local");
        techSession = loginAs("tech@tracetick.local");
    }

    @Test
    void creatingATicketWithoutAuthenticationReturns401() throws Exception {
        mockMvc.perform(post("/api/v1/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "title", "API down",
                                "description", "500 errors"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void customerCanCreateTicketWithTitleAndDescription() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/tickets")
                        .session(aliceSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "title", "API down",
                                "description", "Returns 500"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("API down"))
                .andExpect(jsonPath("$.description").value("Returns 500"))
                .andExpect(jsonPath("$.severity").value("MEDIUM"))
                .andExpect(jsonPath("$.state").value("OPEN"))
                .andExpect(jsonPath("$.origin").value("HUMAN"))
                .andExpect(jsonPath("$.reporterUserId").value(alice.getId()))
                .andReturn();

        Long ticketId = readId(result);
        Ticket persisted = ticketRepository.findById(ticketId).orElseThrow();
        assertThat(persisted.getState()).isEqualTo(TicketState.OPEN);
        assertThat(persisted.getSeverity()).isEqualTo(Severity.MEDIUM);
        assertThat(ticketEventRepository.findAll().stream()
                .filter(e -> e.getTicket().getId().equals(ticketId)).toList()).isEmpty();
    }

    @Test
    void creatingTicketAcceptsOptionalTagsAtCreation() throws Exception {
        mockMvc.perform(post("/api/v1/tickets")
                        .session(aliceSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "title", "API down",
                                "description", "Returns 500",
                                "tags", List.of(
                                        Map.of("key", "service", "value", "api"),
                                        Map.of("key", "env", "value", "prod"))))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tags[?(@.key=='service')].value").value("api"))
                .andExpect(jsonPath("$.tags[?(@.key=='env')].value").value("prod"));
    }

    @Test
    void invalidTagKeyIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/tickets")
                        .session(aliceSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "title", "API down",
                                "description", "Returns 500",
                                "tags", List.of(Map.of("key", "Bad Key", "value", "v"))))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void missingTitleReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/tickets")
                        .session(aliceSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("description", "x"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listWithoutFiltersReturnsOnlyTicketsVisibleToTheRequester() throws Exception {
        createTicketAs(aliceSession, "Alice ticket 1");
        createTicketAs(aliceSession, "Alice ticket 2");
        createTicketAs(bobSession, "Bob ticket");

        mockMvc.perform(get("/api/v1/tickets").session(aliceSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.items[*].title",
                        org.hamcrest.Matchers.containsInAnyOrder("Alice ticket 1", "Alice ticket 2")));

        mockMvc.perform(get("/api/v1/tickets").session(bobSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1));

        mockMvc.perform(get("/api/v1/tickets").session(techSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(3));
    }

    @Test
    void filtersByStateAndSeverity() throws Exception {
        createTicketAs(aliceSession, "Critical issue", "CRITICAL");
        createTicketAs(aliceSession, "High issue", "HIGH");

        mockMvc.perform(get("/api/v1/tickets")
                        .param("severity", "HIGH")
                        .session(aliceSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].title").value("High issue"));
    }

    @Test
    void sortBySeverityDescReturnsHighestPriorityFirst() throws Exception {
        createTicketAs(aliceSession, "Low ticket", "LOW");
        createTicketAs(aliceSession, "Medium ticket", "MEDIUM");
        createTicketAs(aliceSession, "Critical ticket", "CRITICAL");
        createTicketAs(aliceSession, "High ticket", "HIGH");

        mockMvc.perform(get("/api/v1/tickets")
                        .param("sort", "severity,desc")
                        .session(aliceSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].severity",
                        org.hamcrest.Matchers.contains("CRITICAL", "HIGH", "MEDIUM", "LOW")));
    }

    @Test
    void sortByStateAscReturnsLifecycleOrder() throws Exception {
        Long openId = createTicketAs(aliceSession, "Open ticket");
        Long inProgressId = createTicketAs(aliceSession, "In progress ticket");
        Long resolvedId = createTicketAs(aliceSession, "Resolved ticket");
        Long closedId = createTicketAs(aliceSession, "Closed ticket");
        patchAndAssert(inProgressId, techSession, Map.of("state", "IN_PROGRESS"), 200, "IN_PROGRESS");
        patchAndAssert(resolvedId, techSession, Map.of("state", "IN_PROGRESS"), 200, "IN_PROGRESS");
        patchAndAssert(resolvedId, techSession, Map.of("state", "RESOLVED"), 200, "RESOLVED");
        patchAndAssert(closedId, techSession, Map.of("state", "IN_PROGRESS"), 200, "IN_PROGRESS");
        patchAndAssert(closedId, techSession, Map.of("state", "CLOSED"), 200, "CLOSED");

        mockMvc.perform(get("/api/v1/tickets")
                        .param("sort", "state,asc")
                        .session(aliceSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].id",
                        org.hamcrest.Matchers.contains(
                                openId.intValue(),
                                inProgressId.intValue(),
                                resolvedId.intValue(),
                                closedId.intValue())));
    }

    @Test
    void sortByCreatedAtAscReturnsOldestFirst() throws Exception {
        Long firstId = createTicketAs(aliceSession, "First ticket");
        Long secondId = createTicketAs(aliceSession, "Second ticket");
        Long thirdId = createTicketAs(aliceSession, "Third ticket");

        mockMvc.perform(get("/api/v1/tickets")
                        .param("sort", "createdAt,asc")
                        .session(aliceSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].id",
                        org.hamcrest.Matchers.contains(
                                firstId.intValue(),
                                secondId.intValue(),
                                thirdId.intValue())));
    }

    @Test
    void sortWithUnknownFieldReturns400() throws Exception {
        mockMvc.perform(get("/api/v1/tickets")
                        .param("sort", "reporter,desc")
                        .session(aliceSession))
                .andExpect(status().isBadRequest());
    }

    @Test
    void sortWithUnknownDirectionReturns400() throws Exception {
        mockMvc.perform(get("/api/v1/tickets")
                        .param("sort", "severity,sideways")
                        .session(aliceSession))
                .andExpect(status().isBadRequest());
    }

    @Test
    void sortWithoutParamDefaultsToCreatedAtDesc() throws Exception {
        Long firstId = createTicketAs(aliceSession, "First ticket");
        Thread.sleep(10);
        Long secondId = createTicketAs(aliceSession, "Second ticket");

        mockMvc.perform(get("/api/v1/tickets").session(aliceSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].id",
                        org.hamcrest.Matchers.contains(secondId.intValue(), firstId.intValue())));
    }

    @Test
    void paginationIsStableAcrossPagesWhenSortingBySeverity() throws Exception {
        Long firstId = createTicketAs(aliceSession, "Critical one", "CRITICAL");
        Long secondId = createTicketAs(aliceSession, "Critical two", "CRITICAL");
        Long thirdId = createTicketAs(aliceSession, "Critical three", "CRITICAL");
        Long lowId = createTicketAs(aliceSession, "Low one", "LOW");

        MvcResult page1 = mockMvc.perform(get("/api/v1/tickets")
                        .param("sort", "severity,desc")
                        .param("size", "2")
                        .param("page", "0")
                        .session(aliceSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andReturn();

        MvcResult page2 = mockMvc.perform(get("/api/v1/tickets")
                        .param("sort", "severity,desc")
                        .param("size", "2")
                        .param("page", "1")
                        .session(aliceSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andReturn();

        List<Integer> page1Ids = objectMapper.readTree(page1.getResponse().getContentAsString())
                .get("items").findValues("id").stream().map(n -> n.asInt()).toList();
        List<Integer> page2Ids = objectMapper.readTree(page2.getResponse().getContentAsString())
                .get("items").findValues("id").stream().map(n -> n.asInt()).toList();
        List<Integer> combined = new java.util.ArrayList<>();
        combined.addAll(page1Ids);
        combined.addAll(page2Ids);

        assertThat(combined).containsExactly(
                firstId.intValue(),
                secondId.intValue(),
                thirdId.intValue(),
                lowId.intValue());
    }

    @Test
    void filtersByTag() throws Exception {
        mockMvc.perform(post("/api/v1/tickets")
                        .session(aliceSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "title", "API issue",
                                "description", "d",
                                "tags", List.of(Map.of("key", "service", "value", "api"))))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/tickets")
                        .session(aliceSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "title", "Auth issue",
                                "description", "d",
                                "tags", List.of(Map.of("key", "service", "value", "auth"))))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/tickets")
                        .param("tag", "service:api")
                        .session(aliceSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].title").value("API issue"));
    }

    @Test
    void searchFreeTextFiltersByTitleOrDescription() throws Exception {
        createTicketAs(aliceSession, "Database is down");
        createTicketAs(aliceSession, "Login broken");

        mockMvc.perform(get("/api/v1/tickets")
                        .param("search", "login")
                        .session(aliceSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].title").value("Login broken"));
    }

    @Test
    void getByIdReturnsFullDetailWithEventsAndTags() throws Exception {
        Long ticketId = createTicketAs(aliceSession, "API down");

        mockMvc.perform(get("/api/v1/tickets/{id}", ticketId).session(aliceSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticket.id").value(ticketId))
                .andExpect(jsonPath("$.ticket.title").value("API down"))
                .andExpect(jsonPath("$.events").isArray());
    }

    @Test
    void customerCannotViewAnotherCustomersTicket() throws Exception {
        Long ticketId = createTicketAs(aliceSession, "Alice ticket");

        mockMvc.perform(get("/api/v1/tickets/{id}", ticketId).session(bobSession))
                .andExpect(status().isNotFound());
    }

    @Test
    void technicianCanViewAnyTicket() throws Exception {
        Long ticketId = createTicketAs(aliceSession, "Alice ticket");

        mockMvc.perform(get("/api/v1/tickets/{id}", ticketId).session(techSession))
                .andExpect(status().isOk());
    }

    @Test
    void patchTransitionsStateAndAppendsStateChangeEvent() throws Exception {
        Long ticketId = createTicketAs(aliceSession, "API down");

        mockMvc.perform(patch("/api/v1/tickets/{id}", ticketId)
                        .session(techSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("state", "IN_PROGRESS"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("IN_PROGRESS"));

        Ticket persisted = ticketRepository.findById(ticketId).orElseThrow();
        assertThat(persisted.getState()).isEqualTo(TicketState.IN_PROGRESS);

        List<TicketEvent> events = ticketEventRepository.findAll().stream()
                .filter(e -> e.getTicket().getId().equals(ticketId))
                .toList();
        assertThat(events).hasSize(1);
        assertThat(events.get(0).getType()).isEqualTo(com.tracetick.domain.EventType.STATE_CHANGE);
        assertThat(events.get(0).getPayload()).containsEntry("from", "OPEN").containsEntry("to", "IN_PROGRESS");
    }

    @Test
    void patchWithInvalidStateTransitionReturns409() throws Exception {
        Long ticketId = createTicketAs(aliceSession, "API down");

        mockMvc.perform(patch("/api/v1/tickets/{id}", ticketId)
                        .session(techSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("state", "CLOSED"))))
                .andExpect(status().isConflict());
    }

    @Test
    void patchAssignsToTechnician() throws Exception {
        Long ticketId = createTicketAs(aliceSession, "API down");

        mockMvc.perform(patch("/api/v1/tickets/{id}", ticketId)
                        .session(techSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("assigneeUserId", tech.getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assigneeUserId").value(tech.getId()));
    }

    @Test
    void patchAssigningACustomerReturns400() throws Exception {
        Long ticketId = createTicketAs(aliceSession, "API down");

        mockMvc.perform(patch("/api/v1/tickets/{id}", ticketId)
                        .session(techSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("assigneeUserId", bob.getId()))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void patchChangesSeverity() throws Exception {
        Long ticketId = createTicketAs(aliceSession, "API down");

        mockMvc.perform(patch("/api/v1/tickets/{id}", ticketId)
                        .session(techSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("severity", "CRITICAL"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.severity").value("CRITICAL"));
    }

    @Test
    void patchFullLifecycleOpenInProgressResolvedClosed() throws Exception {
        Long ticketId = createTicketAs(aliceSession, "API down");

        patchAndAssert(ticketId, techSession, Map.of("state", "IN_PROGRESS"), 200, "IN_PROGRESS");
        patchAndAssert(ticketId, techSession, Map.of("state", "RESOLVED"), 200, "RESOLVED");
        patchAndAssert(ticketId, techSession, Map.of("state", "CLOSED"), 200, "CLOSED");

        Ticket persisted = ticketRepository.findById(ticketId).orElseThrow();
        assertThat(persisted.getResolvedAt()).isNotNull();
        assertThat(persisted.getClosedAt()).isNotNull();
    }

    @Test
    void reopenFromResolvedGoesBackToInProgress() throws Exception {
        Long ticketId = createTicketAs(aliceSession, "API down");
        patchAndAssert(ticketId, techSession, Map.of("assigneeUserId", tech.getId()), 200, "OPEN");
        patchAndAssert(ticketId, techSession, Map.of("state", "IN_PROGRESS"), 200, "IN_PROGRESS");
        patchAndAssert(ticketId, techSession, Map.of("state", "RESOLVED"), 200, "RESOLVED");
        patchAndAssert(ticketId, aliceSession, Map.of("state", "IN_PROGRESS"), 200, "IN_PROGRESS");

        Ticket persisted = ticketRepository.findById(ticketId).orElseThrow();
        assertThat(persisted.getResolvedAt()).as("resolvedAt cleared on reopen").isNull();
    }

    @Test
    void reopenByUnauthorizedUserReturns409() throws Exception {
        User otherTech = userRepository.saveAndFlush(User.create(stubCustomer(),
                "other-tech@tracetick.local", passwordEncoder.encode("pw"), Role.TECHNICIAN));
        MockHttpSession otherTechSession = loginAs("other-tech@tracetick.local");

        Long ticketId = createTicketAs(aliceSession, "API down");
        patchAndAssert(ticketId, techSession, Map.of("assigneeUserId", tech.getId()), 200, "OPEN");
        patchAndAssert(ticketId, techSession, Map.of("state", "IN_PROGRESS"), 200, "IN_PROGRESS");
        patchAndAssert(ticketId, techSession, Map.of("state", "RESOLVED"), 200, "RESOLVED");

        mockMvc.perform(patch("/api/v1/tickets/{id}", ticketId)
                        .session(otherTechSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("state", "IN_PROGRESS"))))
                .andExpect(status().isConflict());
    }

    @Test
    void closedIsTerminalAndAnyFurtherPatchWithStateReturns409() throws Exception {
        Long ticketId = createTicketAs(aliceSession, "API down");
        patchAndAssert(ticketId, techSession, Map.of("state", "IN_PROGRESS"), 200, "IN_PROGRESS");
        patchAndAssert(ticketId, techSession, Map.of("state", "CLOSED"), 200, "CLOSED");

        mockMvc.perform(patch("/api/v1/tickets/{id}", ticketId)
                        .session(techSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("state", "IN_PROGRESS"))))
                .andExpect(status().isConflict());
    }

    @Test
    void postCommentAppendsCommentEventAndReturns201() throws Exception {
        Long ticketId = createTicketAs(aliceSession, "API down");

        mockMvc.perform(post("/api/v1/tickets/{id}/comments", ticketId)
                        .session(aliceSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("body", "Any update?"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("COMMENT"))
                .andExpect(jsonPath("$.payload.body").value("Any update?"))
                .andExpect(jsonPath("$.actorUserId").value(alice.getId()));

        List<TicketEvent> events = ticketEventRepository.findAll().stream()
                .filter(e -> e.getTicket().getId().equals(ticketId))
                .toList();
        assertThat(events).hasSize(1);
        TicketEvent event = events.get(0);
        assertThat(event.getType()).isEqualTo(com.tracetick.domain.EventType.COMMENT);
        assertThat(event.getPayload()).containsEntry("body", "Any update?");
    }

    @Test
    void customerCannotCommentOnSomeoneElsesTicket() throws Exception {
        Long ticketId = createTicketAs(aliceSession, "API down");

        mockMvc.perform(post("/api/v1/tickets/{id}/comments", ticketId)
                        .session(bobSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("body", "I should not be here"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void postTagAddsTagAndAppendsEvent() throws Exception {
        Long ticketId = createTicketAs(aliceSession, "API down");

        mockMvc.perform(post("/api/v1/tickets/{id}/tags", ticketId)
                        .session(aliceSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("key", "service", "value", "api"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tags[?(@.key=='service')].value").value("api"));

        Ticket persisted = ticketRepository.findById(ticketId).orElseThrow();
        assertThat(persisted.getTags())
                .extracting(t -> t.getKey() + ":" + t.getValue())
                .contains("service:api");
        assertThat(ticketEventRepository.findAll().stream()
                .filter(e -> e.getTicket().getId().equals(ticketId)
                        && e.getType() == com.tracetick.domain.EventType.TAG_CHANGE)
                .count()).isEqualTo(1);
    }

    @Test
    void postTagWithInvalidKeyReturns400() throws Exception {
        Long ticketId = createTicketAs(aliceSession, "API down");

        mockMvc.perform(post("/api/v1/tickets/{id}/tags", ticketId)
                        .session(aliceSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("key", "Bad Key", "value", "v"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deleteTagRemovesTag() throws Exception {
        Long ticketId = createTicketAs(aliceSession, "API down");
        mockMvc.perform(post("/api/v1/tickets/{id}/tags", ticketId)
                        .session(aliceSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("key", "service", "value", "api"))))
                .andExpect(status().isCreated());

        mockMvc.perform(delete("/api/v1/tickets/{id}/tags/service", ticketId)
                        .session(aliceSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tags").isEmpty());
    }

    @Test
    void postWatcherAddsWatcherAndPopulatesDetail() throws Exception {
        Long ticketId = createTicketAs(aliceSession, "API down");

        mockMvc.perform(post("/api/v1/tickets/{id}/watchers", ticketId)
                        .session(aliceSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("userId", bob.getId()))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/tickets/{id}", ticketId).session(aliceSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.watcherIds[0]").value(bob.getId()));
    }

    @Test
    void deleteWatcherRemovesWatcher() throws Exception {
        Long ticketId = createTicketAs(aliceSession, "API down");
        mockMvc.perform(post("/api/v1/tickets/{id}/watchers", ticketId)
                        .session(aliceSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("userId", bob.getId()))))
                .andExpect(status().isCreated());

        mockMvc.perform(delete("/api/v1/tickets/{id}/watchers/{userId}", ticketId, bob.getId())
                        .session(aliceSession))
                .andExpect(status().isOk());

        Ticket persisted = ticketRepository.findById(ticketId).orElseThrow();
        assertThat(persisted.getWatchers()).isEmpty();
    }

    @Test
    void watcherGainVisibilityForCustomer() throws Exception {
        Long bobsTicket = createTicketAs(bobSession, "Bob's ticket");

        mockMvc.perform(post("/api/v1/tickets/{id}/watchers", bobsTicket)
                        .session(bobSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("userId", alice.getId()))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/tickets/{id}", bobsTicket).session(aliceSession))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/tickets").session(aliceSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].title").value("Bob's ticket"));
    }

    @Test
    void customerCannotTagSomeoneElsesTicket() throws Exception {
        Long ticketId = createTicketAs(aliceSession, "API down");

        mockMvc.perform(post("/api/v1/tickets/{id}/tags", ticketId)
                        .session(bobSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("key", "service", "value", "api"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void addWatcherWithUnknownUserReturns404() throws Exception {
        Long ticketId = createTicketAs(aliceSession, "API down");

        mockMvc.perform(post("/api/v1/tickets/{id}/watchers", ticketId)
                        .session(aliceSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("userId", 999_999L))))
                .andExpect(status().isNotFound());
    }

    private void patchAndAssert(Long ticketId, MockHttpSession session,
                                Map<String, Object> body, int expectedStatus, String expectedState)
            throws Exception {
        mockMvc.perform(patch("/api/v1/tickets/{id}", ticketId)
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().is(expectedStatus))
                .andExpect(jsonPath("$.state").value(expectedState));
    }

    private Customer stubCustomer() {
        return customerRepository.findAll().stream().findFirst().orElseThrow();
    }

    private Long createTicketAs(MockHttpSession session, String title) throws Exception {
        return createTicketAs(session, title, null);
    }

    private Long createTicketAs(MockHttpSession session,
                                String title, String severity) throws Exception {
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("title", title);
        body.put("description", "desc");
        if (severity != null) {
            body.put("severity", severity);
        }
        MvcResult result = mockMvc.perform(post("/api/v1/tickets")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isCreated())
                .andReturn();
        return readId(result);
    }

    private Long readId(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private MockHttpSession loginAs(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email, "password", "pw"))))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private byte[] json(Object body) throws Exception {
        return objectMapper.writeValueAsBytes(body);
    }
}
