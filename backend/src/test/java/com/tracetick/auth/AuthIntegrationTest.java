package com.tracetick.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tracetick.domain.Role;
import com.tracetick.persistence.CustomerRepository;
import com.tracetick.persistence.UserRepository;
import jakarta.servlet.http.HttpSession;
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
class AuthIntegrationTest {

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

    @BeforeEach
    void cleanDatabase() {
        userRepository.deleteAllInBatch();
        customerRepository.deleteAllInBatch();
    }

    @Test
    void loginWithValidCredentialsReturns200AndEstablishesSessionExposedOnMe() throws Exception {
        TestFixtures.seedUser(userRepository, customerRepository, passwordEncoder,
                "alex@tracetick.local", "secret123", Role.TECHNICIAN);

        MockHttpSession session = loginAndReturnSession("alex@tracetick.local", "secret123");

        mockMvc.perform(get("/api/v1/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("alex@tracetick.local"));
    }

    @Test
    void loginResponseEstablishesASessionThatSurvivesAcrossRequests() throws Exception {
        TestFixtures.seedUser(userRepository, customerRepository, passwordEncoder,
                "alex@tracetick.local", "secret123", Role.TECHNICIAN);

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("alex@tracetick.local", "secret123")))
                .andExpect(status().isOk())
                .andReturn();

        HttpSession session = loginResult.getRequest().getSession(false);
        assertThat(session).as("login must establish a session").isNotNull();
        assertThat(session.getAttribute("SPRING_SECURITY_CONTEXT")).as("session carries the SecurityContext").isNotNull();

        mockMvc.perform(get("/api/v1/me").session((MockHttpSession) session))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/me").session((MockHttpSession) session))
                .andExpect(status().isOk());
    }

    @Test
    void loginWithInvalidPasswordReturns401() throws Exception {
        TestFixtures.seedUser(userRepository, customerRepository, passwordEncoder,
                "alex@tracetick.local", "secret123", Role.TECHNICIAN);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("alex@tracetick.local", "wrong")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loginWithUnknownEmailReturns401() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("ghost@tracetick.local", "whatever")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loginWithInactiveUserReturns401() throws Exception {
        TestFixtures.seedUser(userRepository, customerRepository, passwordEncoder,
                "alex@tracetick.local", "secret123", Role.TECHNICIAN);
        TestFixtures.deactivate(userRepository, "alex@tracetick.local");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("alex@tracetick.local", "secret123")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void meWithoutSessionReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutInvalidatesSessionSoMeReturns401Afterwards() throws Exception {
        TestFixtures.seedUser(userRepository, customerRepository, passwordEncoder,
                "alex@tracetick.local", "secret123", Role.TECHNICIAN);
        MockHttpSession session = loginAndReturnSession("alex@tracetick.local", "secret123");

        mockMvc.perform(post("/api/v1/auth/logout").session(session))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/me").session(session))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loginEstablishesAServerSessionThatHoldsTheSecurityContext() throws Exception {
        TestFixtures.seedUser(userRepository, customerRepository, passwordEncoder,
                "alex@tracetick.local", "secret123", Role.TECHNICIAN);

        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("alex@tracetick.local", "secret123")))
                .andExpect(status().isOk())
                .andReturn();

        HttpSession session = result.getRequest().getSession(false);
        assertThat(session).as("login creates a servlet session").isNotNull();
        assertThat(session.getId()).as("session has a non-empty id").isNotBlank();
        assertThat(session.getAttribute("SPRING_SECURITY_CONTEXT"))
                .as("session binds the SecurityContext so subsequent requests reuse the auth")
                .isNotNull();
    }

    @Test
    void unauthenticatedUsersListReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/users"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void customerRoleGets403OnAdminEndpoints() throws Exception {
        TestFixtures.seedUser(userRepository, customerRepository, passwordEncoder,
                "customer@tracetick.local", "secret123", Role.CUSTOMER);
        MockHttpSession session = loginAndReturnSession("customer@tracetick.local", "secret123");

        mockMvc.perform(get("/api/v1/users").session(session))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/users").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("newbie@tracetick.local", "freshpass", "CUSTOMER")))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/v1/users/{id}", 1).session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"TECHNICIAN\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void technicianRoleCanListUsersAndReturns200() throws Exception {
        TestFixtures.seedUser(userRepository, customerRepository, passwordEncoder,
                "tech@tracetick.local", "secret123", Role.TECHNICIAN);
        MockHttpSession session = loginAndReturnSession("tech@tracetick.local", "secret123");

        MvcResult listResult = mockMvc.perform(get("/api/v1/users").session(session))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(listResult.getResponse().getContentAsString()).contains("tech@tracetick.local");
    }

    @Test
    void technicianCanCreateUpdateAndDeactivateAUser() throws Exception {
        TestFixtures.seedUser(userRepository, customerRepository, passwordEncoder,
                "tech@tracetick.local", "secret123", Role.TECHNICIAN);
        MockHttpSession session = loginAndReturnSession("tech@tracetick.local", "secret123");

        MvcResult createResult = mockMvc.perform(post("/api/v1/users")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("newbie@tracetick.local", "freshpass", "CUSTOMER")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("newbie@tracetick.local"))
                .andReturn();
        Long newbieId = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(patch("/api/v1/users/{id}", newbieId)
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"TECHNICIAN\",\"active\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("TECHNICIAN"))
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void deactivatedUserCannotLogin() throws Exception {
        TestFixtures.seedUser(userRepository, customerRepository, passwordEncoder,
                "alex@tracetick.local", "secret123", Role.TECHNICIAN);
        TestFixtures.deactivate(userRepository, "alex@tracetick.local");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("alex@tracetick.local", "secret123")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void invalidLoginBodyReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    private MockHttpSession loginAndReturnSession(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(email, password)))
                .andExpect(status().isOk())
                .andReturn();
        HttpSession rawSession = result.getRequest().getSession(false);
        assertThat(rawSession).as("login must establish a session").isNotNull();
        return (MockHttpSession) rawSession;
    }

    private byte[] loginBody(String email, String password) throws Exception {
        return objectMapper.writeValueAsBytes(Map.of("email", email, "password", password));
    }

    private byte[] createBody(String email, String password, String role) throws Exception {
        return objectMapper.writeValueAsBytes(Map.of(
                "email", email,
                "password", password,
                "role", role));
    }
}
