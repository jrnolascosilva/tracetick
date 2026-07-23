package com.tracetick.ingest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tracetick.domain.Customer;
import com.tracetick.domain.EventType;
import com.tracetick.domain.IngestionConfiguration;
import com.tracetick.domain.Role;
import com.tracetick.domain.Severity;
import com.tracetick.domain.Tag;
import com.tracetick.domain.Ticket;
import com.tracetick.domain.TicketEvent;
import com.tracetick.domain.TicketState;
import com.tracetick.domain.User;
import com.tracetick.persistence.CustomerRepository;
import com.tracetick.persistence.IngestionConfigurationRepository;
import com.tracetick.persistence.TicketEventRepository;
import com.tracetick.persistence.TicketRepository;
import com.tracetick.persistence.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
class WebhookIngestIntegrationTest {

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
    private IngestionConfigurationRepository ingestionConfigurationRepository;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private TicketEventRepository ticketEventRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private org.springframework.transaction.support.TransactionTemplate transactionTemplate;

    private Customer customer;
    private User tech;
    private IngestionConfiguration config;

    @BeforeEach
    void setUp() {
        ticketEventRepository.deleteAllInBatch();
        ticketRepository.deleteAllInBatch();
        ingestionConfigurationRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
        customerRepository.deleteAllInBatch();

        customer = customerRepository.saveAndFlush(Customer.create("TraceTick", "ops@tracetick.local"));
        tech = userRepository.saveAndFlush(User.create(customer, "tech@tracetick.local", "h", Role.TECHNICIAN));
        config = ingestionConfigurationRepository.saveAndFlush(IngestionConfiguration.create(
                "PagerDuty",
                "tok-test-123",
                "secret-for-tests-12345678901234567890",
                Severity.MEDIUM,
                tech,
                Map.of("service", "monitoring")));
    }

    @Test
    void validHmacCreatesTicketWithExtractedSeverityAndLabels() throws Exception {
        Map<String, Object> payload = Map.of(
                "alertname", "HighCPU",
                "instance", "host1",
                "alert_id", "abc-123",
                "severity", "critical",
                "labels", Map.of("service", "api", "env", "prod"));
        String body = objectMapper.writeValueAsString(payload);

        mockMvc.perform(post("/api/v1/ingest/" + config.getUrlToken())
                        .header("X-TraceTick-Signature", sign(config.getHmacSecret(), body))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.origin").value("WEBHOOK"))
                .andExpect(jsonPath("$.severity").value("CRITICAL"))
                .andExpect(jsonPath("$.state").value("OPEN"))
                .andExpect(jsonPath("$.reporterUserId").doesNotExist())
                .andExpect(jsonPath("$.assigneeUserId").value(tech.getId()))
                .andExpect(jsonPath("$.refireCount").value(0))
                .andExpect(jsonPath("$.ingestionConfigurationId").value(config.getId()))
                .andExpect(jsonPath("$.fingerprint").value(config.getId() + ":abc-123"))
                .andExpect(jsonPath("$.tags[?(@.key=='service' && @.value=='api')]").exists())
                .andExpect(jsonPath("$.tags[?(@.key=='env' && @.value=='prod')]").exists());

        List<Ticket> tickets = ticketRepository.findAll();
        assertThat(tickets).hasSize(1);
        Ticket ticket = tickets.get(0);
        assertThat(ticket.getFingerprint()).isEqualTo(config.getId() + ":abc-123");
        assertThat(ticket.getRawPayload()).containsEntry("alert_id", "abc-123");
        assertThat(ticket.getIngestionConfiguration()).isNotNull();
        assertThat(ticket.getIngestionConfiguration().getId()).isEqualTo(config.getId());
    }

    @Test
    void missingSignatureHeaderReturns401() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("alertname", "x"));

        mockMvc.perform(post("/api/v1/ingest/" + config.getUrlToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());

        assertThat(ticketRepository.count()).isZero();
    }

    @Test
    void tamperedBodyReturns401() throws Exception {
        String original = objectMapper.writeValueAsString(Map.of("alertname", "x", "severity", "low"));
        String tampered = objectMapper.writeValueAsString(Map.of("alertname", "x", "severity", "critical"));

        mockMvc.perform(post("/api/v1/ingest/" + config.getUrlToken())
                        .header("X-TraceTick-Signature", sign(config.getHmacSecret(), original))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tampered))
                .andExpect(status().isUnauthorized());

        assertThat(ticketRepository.count()).isZero();
    }

    @Test
    void signatureComputedWithWrongSecretReturns401() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("alertname", "x"));

        mockMvc.perform(post("/api/v1/ingest/" + config.getUrlToken())
                        .header("X-TraceTick-Signature", sign("not-the-real-secret", body))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());

        assertThat(ticketRepository.count()).isZero();
    }

    @Test
    void unknownUrlTokenReturns401WithoutLeakingExistence() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("alertname", "x"));

        mockMvc.perform(post("/api/v1/ingest/no-such-token")
                        .header("X-TraceTick-Signature", sign("any", body))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void inactiveConfigurationReturns401() throws Exception {
        config.deactivate();
        ingestionConfigurationRepository.saveAndFlush(config);

        String body = objectMapper.writeValueAsString(Map.of("alertname", "x"));

        mockMvc.perform(post("/api/v1/ingest/" + config.getUrlToken())
                        .header("X-TraceTick-Signature", sign(config.getHmacSecret(), body))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void severityFallsBackToConfigurationDefaultWhenPayloadOmitsIt() throws Exception {
        Map<String, Object> payload = Map.of("alertname", "x", "alert_id", "fp-1");
        String body = objectMapper.writeValueAsString(payload);

        mockMvc.perform(post("/api/v1/ingest/" + config.getUrlToken())
                        .header("X-TraceTick-Signature", sign(config.getHmacSecret(), body))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.severity").value("MEDIUM"));
    }

    @Test
    void severityFallsBackToDefaultWhenPayloadValueIsUnrecognized() throws Exception {
        Map<String, Object> payload = Map.of("alertname", "x", "alert_id", "fp-1", "severity", "page-me");
        String body = objectMapper.writeValueAsString(payload);

        mockMvc.perform(post("/api/v1/ingest/" + config.getUrlToken())
                        .header("X-TraceTick-Signature", sign(config.getHmacSecret(), body))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.severity").value("MEDIUM"));
    }

    @Test
    void firstFireCreatesTicketAndRefireWhileOpenIncrementsAndAppendsRefireEvent() throws Exception {
        Map<String, Object> payload = Map.of(
                "alertname", "HighCPU",
                "instance", "host1",
                "alert_id", "fp-stable");
        String body = objectMapper.writeValueAsString(payload);

        MvcResult first = mockMvc.perform(post("/api/v1/ingest/" + config.getUrlToken())
                        .header("X-TraceTick-Signature", sign(config.getHmacSecret(), body))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.refireCount").value(0))
                .andReturn();
        Long firstTicketId = objectMapper.readTree(first.getResponse().getContentAsString())
                .get("id").asLong();

        mockMvc.perform(post("/api/v1/ingest/" + config.getUrlToken())
                        .header("X-TraceTick-Signature", sign(config.getHmacSecret(), body))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(firstTicketId))
                .andExpect(jsonPath("$.refireCount").value(1));

        assertThat(ticketRepository.count()).isEqualTo(1);
        Ticket ticket = ticketRepository.findById(firstTicketId).orElseThrow();
        List<TicketEvent> refires = ticketEventRepository.findByTicketIdOrderByCreatedAtAscIdAsc(firstTicketId)
                .stream().filter(e -> e.getType() == EventType.REFIRE).toList();
        assertThat(refires).hasSize(1);
        assertThat(refires.get(0).getPayload())
                .containsEntry("refire_count", 1)
                .containsKey("received_at");
        assertThat(refires.get(0).getPayload())
                .as("REFIRE payload is locked by ADR-0004 to {refire_count, received_at}")
                .doesNotContainKey("raw_payload");
    }

    @Test
    void refireWhileResolvedDoesNotAutoReopenAndStillIncrementsCounter() throws Exception {
        Long ticketId = createWebhookTicketAndReturnId("fp-resolved", Map.of("alert_id", "fp-resolved"));
        transitionInTransaction(ticketId, TicketState.IN_PROGRESS, TicketState.RESOLVED);

        Map<String, Object> payload = Map.of("alert_id", "fp-resolved");
        String body = objectMapper.writeValueAsString(payload);

        mockMvc.perform(post("/api/v1/ingest/" + config.getUrlToken())
                        .header("X-TraceTick-Signature", sign(config.getHmacSecret(), body))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ticketId))
                .andExpect(jsonPath("$.state").value("RESOLVED"))
                .andExpect(jsonPath("$.refireCount").value(1));

        assertThat(ticketRepository.count()).isEqualTo(1);
    }

    @Test
    void refireAfterClosedCreatesNewTicket() throws Exception {
        Long ticketId = createWebhookTicketAndReturnId("fp-closed", Map.of("alert_id", "fp-closed"));
        transitionInTransaction(ticketId, TicketState.IN_PROGRESS, TicketState.RESOLVED, TicketState.CLOSED);

        Map<String, Object> payload = Map.of("alert_id", "fp-closed");
        String body = objectMapper.writeValueAsString(payload);

        MvcResult result = mockMvc.perform(post("/api/v1/ingest/" + config.getUrlToken())
                        .header("X-TraceTick-Signature", sign(config.getHmacSecret(), body))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.state").value("OPEN"))
                .andExpect(jsonPath("$.refireCount").value(0))
                .andReturn();
        Long newTicketId = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("id").asLong();

        assertThat(newTicketId).isNotEqualTo(ticketId);
        assertThat(ticketRepository.count()).isEqualTo(2);
    }

    @Test
    void fingerprintIsComposedFromAlertnameAndInstanceWhenNoStableIdIsPresent() throws Exception {
        Map<String, Object> payload = Map.of(
                "alertname", "HighCPU",
                "instance", "host1",
                "severity", "high");
        String body = objectMapper.writeValueAsString(payload);

        mockMvc.perform(post("/api/v1/ingest/" + config.getUrlToken())
                        .header("X-TraceTick-Signature", sign(config.getHmacSecret(), body))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        String expected = config.getId() + ":HighCPU@host1";
        assertThat(ticketRepository.findAll().get(0).getFingerprint()).isEqualTo(expected);
    }

    @Test
    void noIdentityFieldInPayloadMeansNoFingerprintAndEachFireCreatesNewTicket() throws Exception {
        Map<String, Object> payload = Map.of("severity", "low");
        String body = objectMapper.writeValueAsString(payload);

        mockMvc.perform(post("/api/v1/ingest/" + config.getUrlToken())
                        .header("X-TraceTick-Signature", sign(config.getHmacSecret(), body))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/ingest/" + config.getUrlToken())
                        .header("X-TraceTick-Signature", sign(config.getHmacSecret(), body))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        assertThat(ticketRepository.count()).isEqualTo(2);
        assertThat(ticketRepository.findAll()).allMatch(t -> t.getFingerprint() == null);
    }

    @Test
    void defaultTagsAreAppliedWhenPayloadHasNoOverridingLabel() throws Exception {
        Map<String, Object> payload = Map.of(
                "alert_id", "fp-defaults",
                "labels", Map.of("env", "prod"));
        String body = objectMapper.writeValueAsString(payload);

        MvcResult result = mockMvc.perform(post("/api/v1/ingest/" + config.getUrlToken())
                        .header("X-TraceTick-Signature", sign(config.getHmacSecret(), body))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tags[?(@.key=='service' && @.value=='monitoring')]").exists())
                .andExpect(jsonPath("$.tags[?(@.key=='env' && @.value=='prod')]").exists())
                .andReturn();

        Long ticketId = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
        Ticket ticket = ticketRepository.findById(ticketId).orElseThrow();
        assertThat(ticket.getTags())
                .extracting(Tag::getKey, Tag::getValue)
                .contains(
                        org.assertj.core.groups.Tuple.tuple("service", "monitoring"),
                        org.assertj.core.groups.Tuple.tuple("env", "prod"));
    }

    @Test
    void payloadLabelsOverrideConfigurationDefaultsOnKeyCollision() throws Exception {
        Map<String, Object> payload = Map.of(
                "alert_id", "fp-override",
                "labels", Map.of("service", "billing"));
        String body = objectMapper.writeValueAsString(payload);

        MvcResult result = mockMvc.perform(post("/api/v1/ingest/" + config.getUrlToken())
                        .header("X-TraceTick-Signature", sign(config.getHmacSecret(), body))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tags[?(@.key=='service' && @.value=='billing')]").exists())
                .andExpect(jsonPath("$.tags[?(@.key=='service' && @.value=='monitoring')]").doesNotExist())
                .andReturn();

        Long ticketId = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
        Ticket ticket = ticketRepository.findById(ticketId).orElseThrow();
        assertThat(ticket.getTags())
                .extracting(Tag::getKey, Tag::getValue)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("service", "billing"));
    }

    @Test
    void rawPayloadIsStoredOnTheTicket() throws Exception {
        Map<String, Object> payload = Map.of(
                "alert_id", "fp-raw",
                "alertname", "DiskFull",
                "severity", "warning",
                "extra", Map.of("nested", "value"));
        String body = objectMapper.writeValueAsString(payload);

        MvcResult result = mockMvc.perform(post("/api/v1/ingest/" + config.getUrlToken())
                        .header("X-TraceTick-Signature", sign(config.getHmacSecret(), body))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        Long ticketId = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();

        Ticket ticket = ticketRepository.findById(ticketId).orElseThrow();
        assertThat(ticket.getRawPayload())
                .containsEntry("alert_id", "fp-raw")
                .containsEntry("alertname", "DiskFull");
        assertThat((Map<String, Object>) ticket.getRawPayload().get("extra"))
                .containsEntry("nested", "value");
    }

    @Test
    void invalidJsonReturns400() throws Exception {
        String body = "{not-valid-json";

        mockMvc.perform(post("/api/v1/ingest/" + config.getUrlToken())
                        .header("X-TraceTick-Signature", sign(config.getHmacSecret(), body))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void payloadThatIsNotAnObjectReturns400() throws Exception {
        String body = "[1,2,3]";

        mockMvc.perform(post("/api/v1/ingest/" + config.getUrlToken())
                        .header("X-TraceTick-Signature", sign(config.getHmacSecret(), body))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void invalidLabelKeyReturns400() throws Exception {
        Map<String, Object> payload = Map.of(
                "alert_id", "fp-bad-tag",
                "labels", Map.of("BAD KEY", "value"));
        String body = objectMapper.writeValueAsString(payload);

        mockMvc.perform(post("/api/v1/ingest/" + config.getUrlToken())
                        .header("X-TraceTick-Signature", sign(config.getHmacSecret(), body))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void nonStringSeverityReturns400() throws Exception {
        Map<String, Object> payload = Map.of(
                "alert_id", "fp-bad-sev",
                "severity", 42);
        String body = objectMapper.writeValueAsString(payload);

        mockMvc.perform(post("/api/v1/ingest/" + config.getUrlToken())
                        .header("X-TraceTick-Signature", sign(config.getHmacSecret(), body))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void nonStringLabelValueReturns400() throws Exception {
        Map<String, Object> payload = Map.of(
                "alert_id", "fp-bad-label-value",
                "labels", Map.of("service", 5));
        String body = objectMapper.writeValueAsString(payload);

        mockMvc.perform(post("/api/v1/ingest/" + config.getUrlToken())
                        .header("X-TraceTick-Signature", sign(config.getHmacSecret(), body))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void labelsNotAnObjectReturns400() throws Exception {
        Map<String, Object> payload = Map.of(
                "alert_id", "fp-bad-labels",
                "labels", List.of("service", "api"));
        String body = objectMapper.writeValueAsString(payload);

        mockMvc.perform(post("/api/v1/ingest/" + config.getUrlToken())
                        .header("X-TraceTick-Signature", sign(config.getHmacSecret(), body))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void malformedHexSignatureReturns401() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("alertname", "x"));

        mockMvc.perform(post("/api/v1/ingest/" + config.getUrlToken())
                        .header("X-TraceTick-Signature", "sha256=" + "zz".repeat(32))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void missingAssigneeFallsBackToBootstrapCustomer() throws Exception {
        IngestionConfiguration noAssignee = ingestionConfigurationRepository.saveAndFlush(
                IngestionConfiguration.create("NoAssignee", "tok-no-assignee", "secret-no-assignee-1234567890",
                        Severity.LOW, null, Map.of()));

        Map<String, Object> payload = Map.of("alert_id", "fp-no-assignee");
        String body = objectMapper.writeValueAsString(payload);

        MvcResult result = mockMvc.perform(post("/api/v1/ingest/" + noAssignee.getUrlToken())
                        .header("X-TraceTick-Signature", sign(noAssignee.getHmacSecret(), body))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.assigneeUserId").doesNotExist())
                .andExpect(jsonPath("$.severity").value("LOW"))
                .andReturn();
        Long ticketId = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
        Ticket saved = ticketRepository.findById(ticketId).orElseThrow();
        assertThat(saved.getCustomer()).isNotNull();
        assertThat(saved.getCustomer().getId()).isEqualTo(customer.getId());
    }

    @Test
    void titleAndDescriptionAreGenericAndDoNotDeriveFromPayloadFields() throws Exception {
        Map<String, Object> payload = Map.of(
                "alertname", "HighCPU",
                "title", "Should be ignored",
                "summary", "Should be ignored",
                "message", "Should be ignored",
                "description", "Should be ignored",
                "annotations", Map.of("description", "Should be ignored"),
                "alert_id", "fp-generic-text");
        String body = objectMapper.writeValueAsString(payload);

        MvcResult result = mockMvc.perform(post("/api/v1/ingest/" + config.getUrlToken())
                        .header("X-TraceTick-Signature", sign(config.getHmacSecret(), body))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Alert from " + config.getName()))
                .andExpect(jsonPath("$.description").value(
                        "Alert ingested from " + config.getName() + ". See raw_payload for details."))
                .andReturn();
        Long ticketId = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
        Ticket saved = ticketRepository.findById(ticketId).orElseThrow();
        assertThat(saved.getRawPayload()).containsEntry("title", "Should be ignored");
    }

    private Long createWebhookTicketAndReturnId(String title, Map<String, Object> payload) throws Exception {
        String body = objectMapper.writeValueAsString(payload);
        MvcResult result = mockMvc.perform(post("/api/v1/ingest/" + config.getUrlToken())
                        .header("X-TraceTick-Signature", sign(config.getHmacSecret(), body))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private void transitionInTransaction(Long ticketId, TicketState... targetStates) {
        transactionTemplate.executeWithoutResult(status -> {
            Ticket ticket = ticketRepository.findById(ticketId).orElseThrow();
            User actor = ticket.getReporter();
            for (TicketState target : targetStates) {
                ticket.transitionTo(target, actor);
            }
            ticketRepository.saveAndFlush(ticket);
        });
    }

    private static String sign(String secret, String body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] sig = mac.doFinal(body.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(sig.length * 2);
            for (byte b : sig) {
                hex.append(String.format("%02x", b));
            }
            return "sha256=" + hex;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
