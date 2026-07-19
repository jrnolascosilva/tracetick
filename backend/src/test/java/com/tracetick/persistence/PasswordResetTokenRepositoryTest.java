package com.tracetick.persistence;

import com.tracetick.domain.Customer;
import com.tracetick.domain.PasswordResetToken;
import com.tracetick.domain.Role;
import com.tracetick.domain.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class PasswordResetTokenRepositoryTest {

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
    private PasswordResetTokenRepository tokenRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Test
    void lockedHashLookupReturnsPersistedToken() {
        User user = persistUser();
        tokenRepository.saveAndFlush(PasswordResetToken.issue(
                user, "a".repeat(64), Instant.now(), Instant.now().plusSeconds(3600)));

        assertThat(tokenRepository.findByTokenHashForUpdate("a".repeat(64))).isPresent();
    }

    @Test
    void activeLookupExcludesInvalidatedTokens() {
        User user = persistUser();
        PasswordResetToken active = PasswordResetToken.issue(
                user, "a".repeat(64), Instant.now(), Instant.now().plusSeconds(3600));
        PasswordResetToken invalidated = PasswordResetToken.issue(
                user, "b".repeat(64), Instant.now(), Instant.now().plusSeconds(3600));
        invalidated.invalidate(Instant.now());
        tokenRepository.saveAllAndFlush(List.of(active, invalidated));

        assertThat(tokenRepository.findAllByUserIdAndInvalidatedAtIsNull(user.getId()))
                .extracting(PasswordResetToken::getTokenHash)
                .containsExactly("a".repeat(64));
    }

    @Test
    void tokenHashMustBeUnique() {
        User user = persistUser();
        tokenRepository.saveAndFlush(PasswordResetToken.issue(
                user, "a".repeat(64), Instant.now(), Instant.now().plusSeconds(3600)));

        assertThatThrownBy(() -> tokenRepository.saveAndFlush(PasswordResetToken.issue(
                user, "a".repeat(64), Instant.now(), Instant.now().plusSeconds(3600))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private User persistUser() {
        Customer customer = customerRepository.saveAndFlush(
                Customer.create("TraceTick", "ops@tracetick.local"));
        return userRepository.saveAndFlush(User.create(
                customer, "alex@tracetick.local", "hash", Role.TECHNICIAN));
    }
}
