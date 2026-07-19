package com.tracetick.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tracetick.domain.PasswordResetToken;
import com.tracetick.domain.Role;
import com.tracetick.domain.User;
import com.tracetick.persistence.CustomerRepository;
import com.tracetick.persistence.PasswordResetTokenRepository;
import com.tracetick.persistence.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
class PasswordResetIntegrationTest {

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
    private PasswordResetTokenGenerator tokenGenerator;

    @Autowired
    private PasswordResetTokenRepository tokenRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @BeforeEach
    void cleanDatabase() {
        tokenRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
        customerRepository.deleteAllInBatch();
    }

    @Test
    void resetRequestReturnsOneTimeTokenWithoutAuthentication() throws Exception {
        TestFixtures.seedUser(userRepository, customerRepository, passwordEncoder,
                "alex@tracetick.local", "old-password", Role.TECHNICIAN);

        mockMvc.perform(post("/api/v1/auth/password-reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "alex@tracetick.local"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    void validConfirmationUpdatesPassword() throws Exception {
        TestFixtures.seedUser(userRepository, customerRepository, passwordEncoder,
                "alex@tracetick.local", "old-password", Role.TECHNICIAN);
        String token = requestToken("alex@tracetick.local");

        confirm(token, "new-password").andExpect(status().isNoContent());

        User user = userRepository.findByEmail("alex@tracetick.local").orElseThrow();
        assertThat(passwordEncoder.matches("new-password", user.getPasswordHash())).isTrue();
        assertThat(passwordEncoder.matches("old-password", user.getPasswordHash())).isFalse();
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "email", "alex@tracetick.local",
                                "password", "new-password"))))
                .andExpect(status().isOk());
    }

    @Test
    void reusedTokenFails() throws Exception {
        TestFixtures.seedUser(userRepository, customerRepository, passwordEncoder,
                "alex@tracetick.local", "old-password", Role.TECHNICIAN);
        String token = requestToken("alex@tracetick.local");
        confirm(token, "new-password").andExpect(status().isNoContent());

        confirm(token, "another-password").andExpect(status().isConflict());
    }

    @Test
    void expiredTokenFailsWithoutChangingPassword() throws Exception {
        TestFixtures.seedUser(userRepository, customerRepository, passwordEncoder,
                "alex@tracetick.local", "old-password", Role.TECHNICIAN);
        User user = userRepository.findByEmail("alex@tracetick.local").orElseThrow();
        String rawToken = "expired-token";
        tokenRepository.save(PasswordResetToken.issue(
                user, tokenGenerator.hash(rawToken),
                Instant.now().minusSeconds(7200), Instant.now().minusSeconds(3600)));

        confirm(rawToken, "new-password").andExpect(status().isGone());

        User unchanged = userRepository.findByEmail("alex@tracetick.local").orElseThrow();
        assertThat(passwordEncoder.matches("old-password", unchanged.getPasswordHash())).isTrue();
    }

    private String requestToken(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/password-reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("token").asText();
    }

    private ResultActions confirm(String token, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/password-reset/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("token", token, "new_password", password))));
    }

    private byte[] json(Object body) throws Exception {
        return objectMapper.writeValueAsBytes(body);
    }
}
