package com.tracetick.persistence;

import com.tracetick.domain.Customer;
import com.tracetick.domain.Role;
import com.tracetick.domain.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class UserRepositoryTest {

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
    private UserRepository userRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private CustomerRepository customerRepository;

    @Test
    void findByEmailReturnsUserWhenPresent() {
        User persisted = persistUser("alex@tracetick.local", Role.TECHNICIAN);

        Optional<User> found = userRepository.findByEmail("alex@tracetick.local");

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(persisted.getId());
        assertThat(found.get().getRole()).isEqualTo(Role.TECHNICIAN);
    }

    @Test
    void findByEmailReturnsEmptyWhenAbsent() {
        Optional<User> found = userRepository.findByEmail("ghost@tracetick.local");

        assertThat(found).isEmpty();
    }

    @Test
    void emailIsUniquelyIndexedSoDuplicatesCannotPersist() {
        persistUser("dup@tracetick.local", Role.CUSTOMER);
        entityManager.flush();

        User duplicate = User.create(
                customer(),
                "dup@tracetick.local",
                "$2a$10$hash",
                Role.CUSTOMER);

        assertThatExceptionOfType(DataIntegrityViolationException.class)
                .isThrownBy(() -> userRepository.saveAndFlush(duplicate));
    }

    private User persistUser(String email, Role role) {
        User user = User.create(customer(), email, "$2a$10$hash", role);
        return userRepository.saveAndFlush(user);
    }

    private Customer customer() {
        Customer customer = Customer.create("TraceTick", "ops@tracetick.local");
        return customerRepository.saveAndFlush(customer);
    }
}
