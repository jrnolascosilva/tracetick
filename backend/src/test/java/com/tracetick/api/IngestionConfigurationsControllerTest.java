package com.tracetick.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tracetick.domain.Role;
import com.tracetick.persistence.CustomerRepository;
import com.tracetick.persistence.IngestionConfigurationRepository;
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

import java.util.Map;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
class IngestionConfigurationsControllerTest {

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
    private IngestionConfigurationRepository ingestionConfigurationRepository;

    private MockHttpSession techSession;
    private MockHttpSession custSession;

    @BeforeEach
    void cleanDatabase() throws Exception {
        ingestionConfigurationRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
        customerRepository.deleteAllInBatch();

        com.tracetick.auth.TestFixtures.seedUser(userRepository, customerRepository, passwordEncoder,
                "tech@tracetick.local", "secret123", Role.TECHNICIAN);
        com.tracetick.auth.TestFixtures.seedUser(userRepository, customerRepository, passwordEncoder,
                "cust@tracetick.local", "secret123", Role.CUSTOMER);

        techSession = loginAs("tech@tracetick.local");
        custSession = loginAs("cust@tracetick.local");
    }

    @Test
    void unauthenticatedListReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/ingestion-configurations"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void customerRoleGets403OnAllEndpoints() throws Exception {
        mockMvc.perform(get("/api/v1/ingestion-configurations").session(custSession))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/ingestion-configurations").session(custSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "x", "defaultSeverity", "MEDIUM"))))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/v1/ingestion-configurations/1").session(custSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"x\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminListStartsEmpty() throws Exception {
        mockMvc.perform(get("/api/v1/ingestion-configurations").session(techSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void technicianCreatesIngestionConfigurationAndReceivesTokenAndSecretExactlyOnce() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/ingestion-configurations")
                        .session(techSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "name", "PagerDuty",
                                "defaultSeverity", "HIGH",
                                "defaultTags", Map.of("service", "api", "env", "prod")))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("PagerDuty"))
                .andExpect(jsonPath("$.urlToken").isString())
                .andExpect(jsonPath("$.hmacSecret").isString())
                .andExpect(jsonPath("$.webhookUrl").isString())
                .andExpect(jsonPath("$.defaultSeverity").value("HIGH"))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.defaultTags.service").value("api"))
                .andExpect(jsonPath("$.defaultTags.env").value("prod"))
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        String urlToken = body.get("urlToken").asText();
        String hmacSecret = body.get("hmacSecret").asText();
        String webhookUrl = body.get("webhookUrl").asText();

        assertThat(urlToken).matches("^[a-zA-Z0-9_-]{16,128}$");
        assertThat(hmacSecret).matches("^[a-zA-Z0-9_-]{32,128}$");
        assertThat(webhookUrl).endsWith("/" + urlToken);
    }

    @Test
    void secretIsNeverReturnedInSubsequentListOrGetCalls() throws Exception {
        Long id = createConfig(Map.of("name", "PagerDuty", "defaultSeverity", "MEDIUM"));

        mockMvc.perform(get("/api/v1/ingestion-configurations").session(techSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].hmacSecret").doesNotExist());

        mockMvc.perform(get("/api/v1/ingestion-configurations/{id}", id).session(techSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hmacSecret").doesNotExist())
                .andExpect(jsonPath("$.urlToken").isString());
    }

    @Test
    void secondCreateProducesADifferentTokenAndSecret() throws Exception {
        MvcResult first = createConfigResponse(Map.of("name", "A", "defaultSeverity", "MEDIUM"));
        MvcResult second = createConfigResponse(Map.of("name", "B", "defaultSeverity", "MEDIUM"));

        String firstToken = objectMapper.readTree(first.getResponse().getContentAsString()).get("urlToken").asText();
        String secondToken = objectMapper.readTree(second.getResponse().getContentAsString()).get("urlToken").asText();
        String firstSecret = objectMapper.readTree(first.getResponse().getContentAsString()).get("hmacSecret").asText();
        String secondSecret = objectMapper.readTree(second.getResponse().getContentAsString()).get("hmacSecret").asText();

        assertThat(firstToken).isNotEqualTo(secondToken);
        assertThat(firstSecret).isNotEqualTo(secondSecret);
    }

    @Test
    void patchUpdatesFieldsWithoutRotatingSecret() throws Exception {
        Long id = createConfig(Map.of("name", "Old", "defaultSeverity", "LOW", "active", true));

        MvcResult result = mockMvc.perform(patch("/api/v1/ingestion-configurations/{id}", id)
                        .session(techSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "New", "defaultSeverity", "HIGH", "active", false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("New"))
                .andExpect(jsonPath("$.defaultSeverity").value("HIGH"))
                .andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.hmacSecret").doesNotExist())
                .andReturn();

        String persistedSecret = ingestionConfigurationRepository.findById(id).orElseThrow().getHmacSecret();
        assertThat(persistedSecret).as("PATCH without rotateSecret preserves the stored secret")
                .isNotBlank();
    }

    @Test
    void rotateSecretFlagProducesANewSecretThatIsReturnedExactlyOnce() throws Exception {
        Long id = createConfig(Map.of("name", "Rotating", "defaultSeverity", "MEDIUM"));
        String secretBefore = ingestionConfigurationRepository.findById(id).orElseThrow().getHmacSecret();

        MvcResult result = mockMvc.perform(patch("/api/v1/ingestion-configurations/{id}", id)
                        .session(techSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rotate_secret\": true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hmacSecret").isString())
                .andReturn();

        String returnedSecret = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("hmacSecret").asText();
        String persistedAfter = ingestionConfigurationRepository.findById(id).orElseThrow().getHmacSecret();

        assertThat(returnedSecret)
                .as("rotation returns the new secret")
                .isNotBlank()
                .isNotEqualTo(secretBefore);
        assertThat(persistedAfter)
                .as("the new secret is what gets stored")
                .isEqualTo(returnedSecret);
        assertThat(returnedSecret).matches("^[a-zA-Z0-9_-]{32,128}$");

        mockMvc.perform(get("/api/v1/ingestion-configurations/{id}", id).session(techSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hmacSecret").doesNotExist());
    }

    @Test
    void rotateSecretAlsoAcceptsCamelCaseAlias() throws Exception {
        Long id = createConfig(Map.of("name", "Rotating", "defaultSeverity", "MEDIUM"));

        mockMvc.perform(patch("/api/v1/ingestion-configurations/{id}", id)
                        .session(techSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rotateSecret\": true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hmacSecret").isString());
    }

    @Test
    void patchOnUnknownIdReturns404() throws Exception {
        mockMvc.perform(patch("/api/v1/ingestion-configurations/{id}", 999_999L)
                        .session(techSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"new\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void createWithBlankNameReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/ingestion-configurations")
                        .session(techSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "   ", "defaultSeverity", "MEDIUM"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createWithUnknownSeverityReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/ingestion-configurations")
                        .session(techSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "x", "defaultSeverity", "URGENT"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createWithoutSeverityDefaultsToMedium() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/ingestion-configurations")
                        .session(techSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "default-sev", "defaultTags", Map.of("env", "dev")))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.defaultSeverity").value("MEDIUM"))
                .andReturn();

        Long id = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
        mockMvc.perform(get("/api/v1/ingestion-configurations/{id}", id).session(techSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.defaultSeverity").value("MEDIUM"));
    }

    private Long createConfig(Map<String, Object> body) throws Exception {
        MvcResult result = createConfigResponse(body);
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private MvcResult createConfigResponse(Map<String, Object> body) throws Exception {
        return mockMvc.perform(post("/api/v1/ingestion-configurations")
                        .session(techSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isCreated())
                .andReturn();
    }

    private MockHttpSession loginAs(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email, "password", "secret123"))))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private byte[] json(Object body) throws Exception {
        return objectMapper.writeValueAsBytes(body);
    }

    @SuppressWarnings("unused")
    private static final Pattern URL_TOKEN_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]{16,128}$");
}
