# Password Reset Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement issue #4 as a complete password-reset flow with durable single-use tokens, public request/confirmation APIs, a two-step React page, and backend/frontend acceptance coverage.

**Architecture:** A transactional `PasswordResetService` issues 32-byte Base64URL tokens, stores only SHA-256 hashes, invalidates older tokens, and confirms each token under a pessimistic lock. The existing `AuthController` exposes the two API methods; a public React route uses the shared API client and preserves the future `?token=` email-link contract. Backend behavior is driven through domain, service, repository, and MockMvc tests; frontend behavior is driven through Vitest, React Testing Library, and MSW.

**Tech Stack:** Java 21, Spring Boot 3.4.1, Spring Security, Spring Data JPA, Liquibase, PostgreSQL/Testcontainers, JUnit 5, React 19, React Router 7, TypeScript 5.7, Vite 6, Vitest 3.2, React Testing Library, MSW 2.

**Design:** `docs/superpowers/specs/2026-07-18-password-reset-design.md`

---

## File structure

### Backend

- Create `backend/src/main/java/com/tracetick/domain/PasswordResetToken.java`: token lifecycle entity.
- Create `backend/src/main/java/com/tracetick/persistence/PasswordResetTokenRepository.java`: locked hash lookup and active-token lookup.
- Create `backend/src/main/java/com/tracetick/auth/PasswordResetProperties.java`: configurable token lifetime.
- Create `backend/src/main/java/com/tracetick/auth/PasswordResetConfig.java`: `Clock` and `SecureRandom` beans.
- Create `backend/src/main/java/com/tracetick/auth/PasswordResetTokenGenerator.java`: raw-token generation and SHA-256 hashing.
- Create `backend/src/main/java/com/tracetick/auth/PasswordResetException.java`: transport-neutral reset failure reasons.
- Create `backend/src/main/java/com/tracetick/auth/PasswordResetService.java`: transactional request and confirmation orchestration.
- Create `backend/src/main/java/com/tracetick/api/dto/PasswordResetRequest.java`: request DTO.
- Create `backend/src/main/java/com/tracetick/api/dto/PasswordResetResponse.java`: token response DTO.
- Create `backend/src/main/java/com/tracetick/api/dto/PasswordResetConfirmRequest.java`: confirmation DTO with explicit `new_password` mapping.
- Create `backend/src/main/resources/db/changelog/0003-password-reset-tokens.yaml`: token table migration.
- Modify `backend/src/main/java/com/tracetick/domain/User.java`: password-hash replacement behavior.
- Modify `backend/src/main/java/com/tracetick/persistence/UserRepository.java`: pessimistically locked email lookup.
- Modify `backend/src/main/java/com/tracetick/api/AuthController.java`: request and confirm endpoints.
- Modify `backend/src/main/java/com/tracetick/auth/SecurityConfig.java`: anonymous POST access for both endpoints.
- Modify `backend/src/main/java/com/tracetick/TraceTickApplication.java`: configuration-properties registration.
- Modify `backend/src/main/resources/application.yml`: `PT1H` reset-token TTL.
- Modify `backend/src/main/resources/db/changelog/db.changelog-master.yaml`: migration include.
- Create/modify matching tests under `backend/src/test/java/com/tracetick/`.

### Frontend

- Create `frontend/src/test/server.ts`: MSW Node server.
- Create `frontend/src/test/setup.ts`: jest-dom, fetch URL normalization, RTL cleanup, and MSW lifecycle.
- Create `frontend/src/test/renderPasswordReset.tsx`: real-route memory-router renderer.
- Create `frontend/src/lib/apiClient.test.ts`: reset API wire-contract tests.
- Create `frontend/src/pages/PasswordResetPage.tsx`: request, confirm, and success states.
- Create `frontend/src/pages/PasswordResetPage.test.tsx`: rendered-flow acceptance tests.
- Create `frontend/src/pages/LoginPage.test.tsx`: forgot-password discoverability and `next` propagation.
- Modify `frontend/package.json` and `frontend/package-lock.json`: test scripts and dependencies.
- Modify `frontend/vite.config.ts`: jsdom and setup-file configuration.
- Modify `frontend/tsconfig.app.json`: matcher/global test types.
- Modify `frontend/src/lib/types.ts`: reset API types.
- Modify `frontend/src/lib/apiClient.ts`: per-call unauthorized redirect option and reset methods.
- Modify `frontend/src/routes/index.tsx`: exported route objects and public reset route.
- Modify `frontend/src/pages/LoginPage.tsx`: forgot-password link.
- Modify `frontend/src/styles.css`: reset page styling.
- Modify `.github/workflows/ci.yml`: frontend test step.

---

### Task 1: Add token and User domain behavior

**Files:**
- Create: `backend/src/main/java/com/tracetick/domain/PasswordResetToken.java`
- Create: `backend/src/test/java/com/tracetick/domain/PasswordResetTokenTest.java`
- Modify: `backend/src/main/java/com/tracetick/domain/User.java:71-73`
- Modify: `backend/src/test/java/com/tracetick/domain/UserTest.java:39-50`

- [ ] **Step 1: Write the failing User behavior test**

Add this test before `stubCustomer()` in `UserTest.java`:

```java
@Test
void changePasswordHashReplacesStoredHash() {
    User user = User.create(stubCustomer(), "alex@tracetick.local", "old-hash", Role.TECHNICIAN);

    user.changePasswordHash("new-hash");

    assertThat(user.getPasswordHash()).isEqualTo("new-hash");
}
```

- [ ] **Step 2: Write the failing token lifecycle test**

Create `PasswordResetTokenTest.java`:

```java
package com.tracetick.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordResetTokenTest {

    private static final Instant NOW = Instant.parse("2026-07-18T10:00:00Z");

    @Test
    void issueStoresHashAndValidityWindow() {
        User user = User.create(Customer.create("TraceTick", "ops@tracetick.local"),
                "alex@tracetick.local", "hash", Role.TECHNICIAN);

        PasswordResetToken token = PasswordResetToken.issue(
                user, "token-hash", NOW, NOW.plusSeconds(3600));

        assertThat(token.getUser()).isEqualTo(user);
        assertThat(token.getTokenHash()).isEqualTo("token-hash");
        assertThat(token.getCreatedAt()).isEqualTo(NOW);
        assertThat(token.getExpiresAt()).isEqualTo(NOW.plusSeconds(3600));
        assertThat(token.getInvalidatedAt()).isNull();
        assertThat(token.isExpiredAt(NOW)).isFalse();
    }

    @Test
    void expiryBoundaryIsExpired() {
        User user = User.create(Customer.create("TraceTick", "ops@tracetick.local"),
                "alex@tracetick.local", "hash", Role.TECHNICIAN);
        PasswordResetToken token = PasswordResetToken.issue(user, "token-hash", NOW, NOW);

        assertThat(token.isExpiredAt(NOW)).isTrue();
    }

    @Test
    void invalidateRecordsOnlyTheFirstInvalidationInstant() {
        User user = User.create(Customer.create("TraceTick", "ops@tracetick.local"),
                "alex@tracetick.local", "hash", Role.TECHNICIAN);
        PasswordResetToken token = PasswordResetToken.issue(
                user, "token-hash", NOW, NOW.plusSeconds(3600));

        token.invalidate(NOW.plusSeconds(10));
        token.invalidate(NOW.plusSeconds(20));

        assertThat(token.getInvalidatedAt()).isEqualTo(NOW.plusSeconds(10));
    }
}
```

- [ ] **Step 3: Run the focused tests and confirm RED**

Run from `backend/`:

```bash
./gradlew test --tests "com.tracetick.domain.UserTest" --tests "com.tracetick.domain.PasswordResetTokenTest"
```

Expected: compilation fails because `changePasswordHash` and `PasswordResetToken` do not exist.

- [ ] **Step 4: Add minimal User behavior**

Add to `User.java` after `changeRole`:

```java
public void changePasswordHash(String passwordHash) {
    this.passwordHash = passwordHash;
}
```

- [ ] **Step 5: Implement the token entity**

Create `PasswordResetToken.java`:

```java
package com.tracetick.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.Instant;

@Entity
@Table(name = "password_reset_tokens")
@Getter
public class PasswordResetToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "invalidated_at")
    private Instant invalidatedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected PasswordResetToken() {
    }

    private PasswordResetToken(User user, String tokenHash, Instant expiresAt,
                               Instant invalidatedAt, Instant createdAt) {
        this.user = user;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
        this.invalidatedAt = invalidatedAt;
        this.createdAt = createdAt;
    }

    public static PasswordResetToken issue(User user, String tokenHash,
                                            Instant createdAt, Instant expiresAt) {
        return new PasswordResetToken(user, tokenHash, expiresAt, null, createdAt);
    }

    public boolean isExpiredAt(Instant instant) {
        return !expiresAt.isAfter(instant);
    }

    public void invalidate(Instant instant) {
        if (invalidatedAt == null) {
            invalidatedAt = instant;
        }
    }
}
```

- [ ] **Step 6: Run focused tests and confirm GREEN**

```bash
./gradlew test --tests "com.tracetick.domain.UserTest" --tests "com.tracetick.domain.PasswordResetTokenTest"
```

Expected: both classes pass.

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/com/tracetick/domain/User.java \
  backend/src/main/java/com/tracetick/domain/PasswordResetToken.java \
  backend/src/test/java/com/tracetick/domain/UserTest.java \
  backend/src/test/java/com/tracetick/domain/PasswordResetTokenTest.java
git commit -m "feat(auth): model reset token lifecycle"
```

---

### Task 2: Persist and lock reset tokens

**Files:**
- Create: `backend/src/main/resources/db/changelog/0003-password-reset-tokens.yaml`
- Create: `backend/src/main/java/com/tracetick/persistence/PasswordResetTokenRepository.java`
- Create: `backend/src/test/java/com/tracetick/persistence/PasswordResetTokenRepositoryTest.java`
- Modify: `backend/src/main/resources/db/changelog/db.changelog-master.yaml:1-5`
- Modify: `backend/src/main/java/com/tracetick/persistence/UserRepository.java:1-11`

- [ ] **Step 1: Write the failing repository test**

Create `PasswordResetTokenRepositoryTest.java` by following the existing Testcontainers repository-test annotations and adding these behaviors:

```java
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
```

- [ ] **Step 2: Run the repository test and confirm RED**

```bash
./gradlew test --tests "com.tracetick.persistence.PasswordResetTokenRepositoryTest"
```

Expected: compilation fails because `PasswordResetTokenRepository` does not exist.

- [ ] **Step 3: Add the Liquibase changeset**

Create `0003-password-reset-tokens.yaml`:

```yaml
databaseChangeLog:
  - changeSet:
      id: 0003-password-reset-tokens
      author: tracetick
      comment: T3 — Password reset. Stores hashes and lifecycle timestamps for one-time reset tokens.
      changes:
        - createTable:
            tableName: password_reset_tokens
            columns:
              - column:
                  name: id
                  type: bigint
                  autoIncrement: true
                  constraints:
                    primaryKey: true
                    nullable: false
              - column:
                  name: user_id
                  type: bigint
                  constraints:
                    nullable: false
                    foreignKeyName: fk_password_reset_tokens_user
                    references: users(id)
              - column:
                  name: token_hash
                  type: varchar(64)
                  constraints:
                    nullable: false
                    unique: true
                    uniqueConstraintName: uk_password_reset_tokens_token_hash
              - column:
                  name: expires_at
                  type: timestamp with time zone
                  constraints:
                    nullable: false
              - column:
                  name: invalidated_at
                  type: timestamp with time zone
              - column:
                  name: created_at
                  type: timestamp with time zone
                  constraints:
                    nullable: false
        - createIndex:
            tableName: password_reset_tokens
            indexName: idx_password_reset_tokens_user_id
            columns:
              - column:
                  name: user_id
      rollback:
        - dropTable:
            tableName: password_reset_tokens
```

Append to `db.changelog-master.yaml`:

```yaml
  - include:
      file: db/changelog/0003-password-reset-tokens.yaml
```

- [ ] **Step 4: Add the token repository**

Create `PasswordResetTokenRepository.java`:

```java
package com.tracetick.persistence;

import com.tracetick.domain.PasswordResetToken;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select token from PasswordResetToken token where token.tokenHash = :tokenHash")
    Optional<PasswordResetToken> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    List<PasswordResetToken> findAllByUserIdAndInvalidatedAtIsNull(Long userId);
}
```

- [ ] **Step 5: Add the locked User lookup**

Replace `UserRepository.java` with:

```java
package com.tracetick.persistence;

import com.tracetick.domain.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select user from User user where user.email = :email")
    Optional<User> findByEmailForUpdate(@Param("email") String email);
}
```

- [ ] **Step 6: Run repository tests and backend compilation**

```bash
./gradlew test --tests "com.tracetick.persistence.PasswordResetTokenRepositoryTest"
./gradlew compileJava
```

Expected: repository tests pass and compilation succeeds.

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/resources/db/changelog/0003-password-reset-tokens.yaml \
  backend/src/main/resources/db/changelog/db.changelog-master.yaml \
  backend/src/main/java/com/tracetick/persistence/PasswordResetTokenRepository.java \
  backend/src/main/java/com/tracetick/persistence/UserRepository.java \
  backend/src/test/java/com/tracetick/persistence/PasswordResetTokenRepositoryTest.java
git commit -m "feat(auth): persist reset tokens"
```

---

### Task 3: Issue real and decoy tokens

**Files:**
- Create: `backend/src/main/java/com/tracetick/auth/PasswordResetProperties.java`
- Create: `backend/src/main/java/com/tracetick/auth/PasswordResetConfig.java`
- Create: `backend/src/main/java/com/tracetick/auth/PasswordResetTokenGenerator.java`
- Create: `backend/src/main/java/com/tracetick/auth/PasswordResetService.java`
- Create: `backend/src/test/java/com/tracetick/auth/PasswordResetTokenGeneratorTest.java`
- Create: `backend/src/test/java/com/tracetick/auth/PasswordResetServiceTest.java`
- Modify: `backend/src/main/java/com/tracetick/TraceTickApplication.java:3-10`
- Modify: `backend/src/main/resources/application.yml:36-40`

- [ ] **Step 1: Write generator tests**

Create `PasswordResetTokenGeneratorTest.java`:

```java
package com.tracetick.auth;

import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordResetTokenGeneratorTest {

    private final PasswordResetTokenGenerator generator =
            new PasswordResetTokenGenerator(new SecureRandom());

    @Test
    void generatedTokenIsUnpaddedBase64UrlFor32Bytes() {
        String token = generator.generate();

        assertThat(token).hasSize(43).matches("^[A-Za-z0-9_-]+$");
        assertThat(Base64.getUrlDecoder().decode(token)).hasSize(32);
    }

    @Test
    void hashMatchesKnownSha256Value() {
        assertThat(generator.hash("abc")).isEqualTo(
                "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }
}
```

- [ ] **Step 2: Write issuance service tests**

Create `PasswordResetServiceTest.java` with issuance behavior first:

```java
package com.tracetick.auth;

import com.tracetick.domain.Customer;
import com.tracetick.domain.PasswordResetToken;
import com.tracetick.domain.Role;
import com.tracetick.domain.User;
import com.tracetick.persistence.PasswordResetTokenRepository;
import com.tracetick.persistence.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PasswordResetServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-18T10:00:00Z");

    private UserRepository userRepository;
    private PasswordResetTokenRepository tokenRepository;
    private PasswordResetTokenGenerator generator;
    private PasswordEncoder passwordEncoder;
    private PasswordResetService service;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        tokenRepository = mock(PasswordResetTokenRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        generator = new PasswordResetTokenGenerator(new SecureRandom());
        service = new PasswordResetService(userRepository, tokenRepository, passwordEncoder,
                generator, new PasswordResetProperties(Duration.ofHours(1)),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void activeUserGetsPersistedTokenWithConfiguredExpiry() {
        User user = activeUser();
        when(userRepository.findByEmailForUpdate(user.getEmail())).thenReturn(Optional.of(user));
        when(tokenRepository.findAllByUserIdAndInvalidatedAtIsNull(user.getId()))
                .thenReturn(List.of());

        String rawToken = service.requestReset(user.getEmail());

        assertThat(rawToken).hasSize(43);
        ArgumentCaptor<PasswordResetToken> captor =
                ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(tokenRepository).save(captor.capture());
        assertThat(captor.getValue().getTokenHash()).isEqualTo(generator.hash(rawToken));
        assertThat(captor.getValue().getCreatedAt()).isEqualTo(NOW);
        assertThat(captor.getValue().getExpiresAt()).isEqualTo(NOW.plusSeconds(3600));
    }

    @Test
    void unknownEmailGetsUnpersistedDecoyWithRealTokenShape() {
        when(userRepository.findByEmailForUpdate("ghost@tracetick.local"))
                .thenReturn(Optional.empty());

        String token = service.requestReset("ghost@tracetick.local");

        assertThat(token).hasSize(43).matches("^[A-Za-z0-9_-]+$");
        verify(tokenRepository, never()).save(any());
    }

    @Test
    void inactiveUserGetsUnpersistedDecoy() {
        User user = activeUser();
        user.deactivate();
        when(userRepository.findByEmailForUpdate(user.getEmail())).thenReturn(Optional.of(user));

        String token = service.requestReset(user.getEmail());

        assertThat(token).hasSize(43);
        verify(tokenRepository, never()).save(any());
    }

    @Test
    void newerRequestInvalidatesOlderUnusedTokens() {
        User user = activeUser();
        PasswordResetToken oldToken = PasswordResetToken.issue(
                user, "a".repeat(64), NOW.minusSeconds(60), NOW.plusSeconds(300));
        when(userRepository.findByEmailForUpdate(user.getEmail())).thenReturn(Optional.of(user));
        when(tokenRepository.findAllByUserIdAndInvalidatedAtIsNull(user.getId()))
                .thenReturn(List.of(oldToken));

        service.requestReset(user.getEmail());

        assertThat(oldToken.getInvalidatedAt()).isEqualTo(NOW);
        verify(tokenRepository).saveAll(List.of(oldToken));
    }

    private static User activeUser() {
        return User.create(Customer.create("TraceTick", "ops@tracetick.local"),
                "alex@tracetick.local", "old-hash", Role.TECHNICIAN);
    }
}
```

- [ ] **Step 3: Run focused tests and confirm RED**

```bash
./gradlew test --tests "com.tracetick.auth.PasswordResetTokenGeneratorTest" \
  --tests "com.tracetick.auth.PasswordResetServiceTest"
```

Expected: compilation fails because the generator, properties, and service do not exist.

- [ ] **Step 4: Implement properties and bean configuration**

Create `PasswordResetProperties.java`:

```java
package com.tracetick.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "tracetick.auth.password-reset")
public record PasswordResetProperties(Duration tokenTtl) {
}
```

Create `PasswordResetConfig.java`:

```java
package com.tracetick.auth;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.security.SecureRandom;
import java.time.Clock;

@Configuration
public class PasswordResetConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public SecureRandom secureRandom() {
        return new SecureRandom();
    }
}
```

Change `TraceTickApplication.java` imports and annotation to:

```java
import com.tracetick.auth.BootstrapProperties;
import com.tracetick.auth.PasswordResetProperties;

@EnableConfigurationProperties({BootstrapProperties.class, PasswordResetProperties.class})
```

Extend the existing `tracetick` block in `application.yml`:

```yaml
tracetick:
  bootstrap:
    enabled: ${TRACETICK_BOOTSTRAP_ENABLED:true}
    admin-email: ${TRACETICK_BOOTSTRAP_ADMIN_EMAIL:admin@tracetick.local}
    admin-password: ${TRACETICK_BOOTSTRAP_ADMIN_PASSWORD:changeme}
  auth:
    password-reset:
      token-ttl: ${TRACETICK_PASSWORD_RESET_TOKEN_TTL:PT1H}
```

- [ ] **Step 5: Implement the generator**

Create `PasswordResetTokenGenerator.java`:

```java
package com.tracetick.auth;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

@Component
public class PasswordResetTokenGenerator {

    private static final int TOKEN_BYTES = 32;

    private final SecureRandom secureRandom;

    public PasswordResetTokenGenerator(SecureRandom secureRandom) {
        this.secureRandom = secureRandom;
    }

    public String generate() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public String hash(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
```

- [ ] **Step 6: Implement request issuance**

Create `PasswordResetService.java` with the request method:

```java
package com.tracetick.auth;

import com.tracetick.domain.PasswordResetToken;
import com.tracetick.domain.User;
import com.tracetick.persistence.PasswordResetTokenRepository;
import com.tracetick.persistence.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
public class PasswordResetService {

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordResetTokenGenerator tokenGenerator;
    private final PasswordResetProperties properties;
    private final Clock clock;

    public PasswordResetService(UserRepository userRepository,
                                PasswordResetTokenRepository tokenRepository,
                                PasswordEncoder passwordEncoder,
                                PasswordResetTokenGenerator tokenGenerator,
                                PasswordResetProperties properties,
                                Clock clock) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenGenerator = tokenGenerator;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public String requestReset(String email) {
        String rawToken = tokenGenerator.generate();
        Optional<User> user = userRepository.findByEmailForUpdate(email)
                .filter(User::isActive);
        if (user.isEmpty()) {
            return rawToken;
        }

        Instant now = clock.instant();
        List<PasswordResetToken> olderTokens =
                tokenRepository.findAllByUserIdAndInvalidatedAtIsNull(user.get().getId());
        olderTokens.forEach(token -> token.invalidate(now));
        tokenRepository.saveAll(olderTokens);

        PasswordResetToken token = PasswordResetToken.issue(
                user.get(), tokenGenerator.hash(rawToken), now,
                now.plus(properties.tokenTtl()));
        tokenRepository.save(token);
        return rawToken;
    }
}
```

- [ ] **Step 7: Run focused tests and compilation**

```bash
./gradlew test --tests "com.tracetick.auth.PasswordResetTokenGeneratorTest" \
  --tests "com.tracetick.auth.PasswordResetServiceTest"
./gradlew compileJava
```

Expected: tests pass and Spring components compile.

- [ ] **Step 8: Commit**

```bash
git add backend/src/main/java/com/tracetick/auth/PasswordResetProperties.java \
  backend/src/main/java/com/tracetick/auth/PasswordResetConfig.java \
  backend/src/main/java/com/tracetick/auth/PasswordResetTokenGenerator.java \
  backend/src/main/java/com/tracetick/auth/PasswordResetService.java \
  backend/src/main/java/com/tracetick/TraceTickApplication.java \
  backend/src/main/resources/application.yml \
  backend/src/test/java/com/tracetick/auth/PasswordResetTokenGeneratorTest.java \
  backend/src/test/java/com/tracetick/auth/PasswordResetServiceTest.java
git commit -m "feat(auth): issue password reset tokens"
```

---

### Task 4: Confirm tokens and replace passwords

**Files:**
- Create: `backend/src/main/java/com/tracetick/auth/PasswordResetException.java`
- Modify: `backend/src/main/java/com/tracetick/auth/PasswordResetService.java`
- Modify: `backend/src/test/java/com/tracetick/auth/PasswordResetServiceTest.java`

- [ ] **Step 1: Add confirmation tests**

Add these tests to `PasswordResetServiceTest.java`:

```java
@Test
void validConfirmationChangesHashAndInvalidatesToken() {
    User user = activeUser();
    PasswordResetToken token = PasswordResetToken.issue(
            user, generator.hash("raw-token"), NOW.minusSeconds(60), NOW.plusSeconds(300));
    when(tokenRepository.findByTokenHashForUpdate(generator.hash("raw-token")))
            .thenReturn(Optional.of(token));
    when(passwordEncoder.encode("new-password")).thenReturn("new-hash");

    service.confirmReset("raw-token", "new-password");

    assertThat(user.getPasswordHash()).isEqualTo("new-hash");
    assertThat(token.getInvalidatedAt()).isEqualTo(NOW);
    verify(userRepository).save(user);
    verify(tokenRepository).save(token);
}

@Test
void unknownTokenIsInvalid() {
    when(tokenRepository.findByTokenHashForUpdate(generator.hash("unknown")))
            .thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.confirmReset("unknown", "new-password"))
            .isInstanceOfSatisfying(PasswordResetException.class,
                    exception -> assertThat(exception.getReason())
                            .isEqualTo(PasswordResetException.Reason.INVALID));
}

@Test
void reusedTokenIsInvalidated() {
    User user = activeUser();
    PasswordResetToken token = PasswordResetToken.issue(
            user, generator.hash("used"), NOW.minusSeconds(60), NOW.plusSeconds(300));
    token.invalidate(NOW.minusSeconds(10));
    when(tokenRepository.findByTokenHashForUpdate(generator.hash("used")))
            .thenReturn(Optional.of(token));

    assertThatThrownBy(() -> service.confirmReset("used", "new-password"))
            .isInstanceOfSatisfying(PasswordResetException.class,
                    exception -> assertThat(exception.getReason())
                            .isEqualTo(PasswordResetException.Reason.INVALIDATED));
    assertThat(user.getPasswordHash()).isEqualTo("old-hash");
}

@Test
void expiredTokenIsExpiredAndLeavesPasswordUnchanged() {
    User user = activeUser();
    PasswordResetToken token = PasswordResetToken.issue(
            user, generator.hash("expired"), NOW.minusSeconds(3600), NOW.minusSeconds(1));
    when(tokenRepository.findByTokenHashForUpdate(generator.hash("expired")))
            .thenReturn(Optional.of(token));

    assertThatThrownBy(() -> service.confirmReset("expired", "new-password"))
            .isInstanceOfSatisfying(PasswordResetException.class,
                    exception -> assertThat(exception.getReason())
                            .isEqualTo(PasswordResetException.Reason.EXPIRED));
    assertThat(user.getPasswordHash()).isEqualTo("old-hash");
}
```

Add this static import:

```java
import static org.assertj.core.api.Assertions.assertThatThrownBy;
```

- [ ] **Step 2: Run confirmation tests and confirm RED**

```bash
./gradlew test --tests "com.tracetick.auth.PasswordResetServiceTest"
```

Expected: compilation fails because `confirmReset` and `PasswordResetException` do not exist.

- [ ] **Step 3: Add transport-neutral failure reasons**

Create `PasswordResetException.java`:

```java
package com.tracetick.auth;

public class PasswordResetException extends RuntimeException {

    public enum Reason {
        INVALID,
        INVALIDATED,
        EXPIRED
    }

    private final Reason reason;

    private PasswordResetException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public static PasswordResetException invalid() {
        return new PasswordResetException(Reason.INVALID, "Invalid password reset token");
    }

    public static PasswordResetException invalidated() {
        return new PasswordResetException(Reason.INVALIDATED, "Password reset token has already been used");
    }

    public static PasswordResetException expired() {
        return new PasswordResetException(Reason.EXPIRED, "Password reset token has expired");
    }

    public Reason getReason() {
        return reason;
    }
}
```

- [ ] **Step 4: Add confirmation to the service**

Append to `PasswordResetService.java`:

```java
@Transactional
public void confirmReset(String rawToken, String newPassword) {
    PasswordResetToken token = tokenRepository
            .findByTokenHashForUpdate(tokenGenerator.hash(rawToken))
            .orElseThrow(PasswordResetException::invalid);

    Instant now = clock.instant();
    if (token.getInvalidatedAt() != null) {
        throw PasswordResetException.invalidated();
    }
    if (token.isExpiredAt(now)) {
        throw PasswordResetException.expired();
    }

    User user = token.getUser();
    user.changePasswordHash(passwordEncoder.encode(newPassword));
    token.invalidate(now);
    userRepository.save(user);
    tokenRepository.save(token);
}
```

- [ ] **Step 5: Run service tests and backend compilation**

```bash
./gradlew test --tests "com.tracetick.auth.PasswordResetServiceTest"
./gradlew compileJava
```

Expected: all request and confirmation service tests pass.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/tracetick/auth/PasswordResetException.java \
  backend/src/main/java/com/tracetick/auth/PasswordResetService.java \
  backend/src/test/java/com/tracetick/auth/PasswordResetServiceTest.java
git commit -m "feat(auth): confirm password reset tokens"
```

---

### Task 5: Expose and accept the password-reset API

**Files:**
- Create: `backend/src/main/java/com/tracetick/api/dto/PasswordResetRequest.java`
- Create: `backend/src/main/java/com/tracetick/api/dto/PasswordResetResponse.java`
- Create: `backend/src/main/java/com/tracetick/api/dto/PasswordResetConfirmRequest.java`
- Create: `backend/src/test/java/com/tracetick/auth/PasswordResetIntegrationTest.java`
- Modify: `backend/src/main/java/com/tracetick/api/AuthController.java:3-46,81`
- Modify: `backend/src/main/java/com/tracetick/auth/SecurityConfig.java:3-44`

- [ ] **Step 1: Write HTTP acceptance tests**

Create `PasswordResetIntegrationTest.java` with the four issue acceptance cases:

```java
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

    private org.springframework.test.web.servlet.ResultActions confirm(
            String token, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/password-reset/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("token", token, "new_password", password))));
    }

    private byte[] json(Object body) throws Exception {
        return objectMapper.writeValueAsBytes(body);
    }
}
```

- [ ] **Step 2: Run the acceptance test and confirm RED**

```bash
./gradlew test --tests "com.tracetick.auth.PasswordResetIntegrationTest"
```

Expected: requests return 401 or 404 because DTOs, endpoints, and permit rules do not exist.

- [ ] **Step 3: Add request and response DTOs**

Create `PasswordResetRequest.java`:

```java
package com.tracetick.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordResetRequest(
        @NotBlank @Email @Size(max = 255) String email) {
}
```

Create `PasswordResetResponse.java`:

```java
package com.tracetick.api.dto;

public record PasswordResetResponse(String token) {
}
```

Create `PasswordResetConfirmRequest.java`:

```java
package com.tracetick.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordResetConfirmRequest(
        @NotBlank @Size(max = 255) String token,
        @JsonProperty("new_password")
        @NotBlank @Size(min = 8, max = 255) String newPassword) {
}
```

- [ ] **Step 4: Add controller methods**

Add `PasswordResetService` to the existing `AuthController` constructor and fields, import the three DTOs plus `PasswordResetException`, `HttpStatus`, and `ResponseStatusException`, then add:

```java
@PostMapping("/password-reset")
public ResponseEntity<PasswordResetResponse> requestPasswordReset(
        @Valid @RequestBody PasswordResetRequest request) {
    return ResponseEntity.ok(new PasswordResetResponse(
            passwordResetService.requestReset(request.email())));
}

@PostMapping("/password-reset/confirm")
public ResponseEntity<Void> confirmPasswordReset(
        @Valid @RequestBody PasswordResetConfirmRequest request) {
    try {
        passwordResetService.confirmReset(request.token(), request.newPassword());
        return ResponseEntity.noContent().build();
    } catch (PasswordResetException exception) {
        HttpStatus status = switch (exception.getReason()) {
            case INVALID -> HttpStatus.BAD_REQUEST;
            case INVALIDATED -> HttpStatus.CONFLICT;
            case EXPIRED -> HttpStatus.GONE;
        };
        throw new ResponseStatusException(status, exception.getMessage(), exception);
    }
}
```

The updated constructor signature is:

```java
public AuthController(AuthenticationManager authenticationManager,
                      UserRepository userRepository,
                      SecurityContextRepository securityContextRepository,
                      PasswordResetService passwordResetService) {
    this.authenticationManager = authenticationManager;
    this.userRepository = userRepository;
    this.securityContextRepository = securityContextRepository;
    this.passwordResetService = passwordResetService;
}
```

- [ ] **Step 5: Permit only anonymous POST reset requests**

Import `org.springframework.http.HttpMethod` and insert before health matchers in `SecurityConfig.java`:

```java
.requestMatchers(HttpMethod.POST,
        "/api/v1/auth/password-reset",
        "/api/v1/auth/password-reset/confirm").permitAll()
```

- [ ] **Step 6: Run focused backend checks**

```bash
./gradlew test --tests "com.tracetick.auth.PasswordResetIntegrationTest"
./gradlew test --tests "com.tracetick.auth.AuthIntegrationTest"
./gradlew compileJava
```

Expected: all password-reset acceptance cases pass, existing auth behavior stays green, and compilation succeeds.

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/com/tracetick/api/AuthController.java \
  backend/src/main/java/com/tracetick/api/dto/PasswordResetRequest.java \
  backend/src/main/java/com/tracetick/api/dto/PasswordResetResponse.java \
  backend/src/main/java/com/tracetick/api/dto/PasswordResetConfirmRequest.java \
  backend/src/main/java/com/tracetick/auth/SecurityConfig.java \
  backend/src/test/java/com/tracetick/auth/PasswordResetIntegrationTest.java
git commit -m "feat(api): expose password reset flow"
```

---

### Task 6: Bootstrap frontend tests and reset API methods

**Files:**
- Create: `frontend/src/test/server.ts`
- Create: `frontend/src/test/setup.ts`
- Create: `frontend/src/lib/apiClient.test.ts`
- Modify: `frontend/package.json`
- Modify: `frontend/package-lock.json`
- Modify: `frontend/vite.config.ts:1-21`
- Modify: `frontend/tsconfig.app.json:17-27`
- Modify: `frontend/src/lib/types.ts:10-24`
- Modify: `frontend/src/lib/apiClient.ts:1-69`

- [ ] **Step 1: Install the approved test harness**

Run from `frontend/`:

```bash
npm install --save-dev vitest@3.2.4 jsdom@29.1.1 \
  @testing-library/react@16.3.2 @testing-library/jest-dom@6.9.1 \
  @testing-library/user-event@14.6.1 @testing-library/dom@10.4.1 msw@2.15.0
```

Expected: `package.json` and `package-lock.json` update without peer-dependency errors.

- [ ] **Step 2: Add scripts and Vitest configuration**

Add to `package.json` scripts:

```json
"test": "vitest run",
"test:watch": "vitest"
```

Change the first import in `vite.config.ts` and add the test block:

```ts
import { defineConfig } from 'vitest/config';

export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: process.env.VITE_API_PROXY ?? 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
  },
});
```

Add to `tsconfig.app.json` compiler options:

```json
"types": ["vitest/globals", "@testing-library/jest-dom"]
```

- [ ] **Step 3: Add the MSW test lifecycle**

Create `server.ts`:

```ts
import { setupServer } from 'msw/node';

export const server = setupServer();
```

Create `setup.ts`:

```ts
import '@testing-library/jest-dom/vitest';
import { cleanup } from '@testing-library/react';
import { afterAll, afterEach, beforeAll } from 'vitest';

import { server } from '@/test/server';

const nativeFetch = globalThis.fetch.bind(globalThis);

globalThis.fetch = (input, init) =>
  nativeFetch(typeof input === 'string' ? new URL(input, window.location.origin) : input, init);

beforeAll(() => server.listen({ onUnhandledRequest: 'error' }));
afterEach(() => {
  cleanup();
  server.resetHandlers();
});
afterAll(() => server.close());
```

- [ ] **Step 4: Write failing API wire-contract tests**

Create `apiClient.test.ts`:

```ts
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';

import { ApiError, apiClient } from '@/lib/apiClient';
import { server } from '@/test/server';

describe('password reset API client', () => {
  it('requests a token with the email wire shape', async () => {
    server.use(
      http.post('/api/v1/auth/password-reset', async ({ request }) => {
        expect(await request.json()).toEqual({ email: 'alex@example.com' });
        return HttpResponse.json({ token: 'returned-token' });
      }),
    );

    await expect(apiClient.requestPasswordReset({ email: 'alex@example.com' }))
      .resolves.toEqual({ token: 'returned-token' });
  });

  it('confirms with new_password and accepts 204', async () => {
    server.use(
      http.post('/api/v1/auth/password-reset/confirm', async ({ request }) => {
        expect(await request.json()).toEqual({
          token: 'returned-token',
          new_password: 'new-password',
        });
        return new HttpResponse(null, { status: 204 });
      }),
    );

    await expect(apiClient.confirmPasswordReset({
      token: 'returned-token',
      new_password: 'new-password',
    })).resolves.toBeUndefined();
  });

  it('does not navigate when a reset endpoint returns 401', async () => {
    server.use(
      http.post('/api/v1/auth/password-reset', () =>
        new HttpResponse(null, { status: 401 })),
    );

    await expect(apiClient.requestPasswordReset({ email: 'alex@example.com' }))
      .rejects.toEqual(expect.objectContaining<ApiError>({ status: 401 }));
    expect(window.location.pathname).toBe('/');
  });
});
```

- [ ] **Step 5: Run the API client test and confirm RED**

```bash
npm test -- src/lib/apiClient.test.ts
```

Expected: TypeScript compilation or tests fail because reset types and methods do not exist.

- [ ] **Step 6: Add reset API types**

Append to `types.ts`:

```ts
export interface PasswordResetRequest {
  email: string;
}

export interface PasswordResetResponse {
  token: string;
}

export interface PasswordResetConfirmRequest {
  token: string;
  new_password: string;
}
```

- [ ] **Step 7: Add per-call redirect control and reset methods**

Expand the `apiClient.ts` type import to include the three reset types. Add:

```ts
interface RequestOptions {
  redirectOnUnauthorized?: boolean;
}
```

Change the request signature and 401 condition:

```ts
async function request<T>(
  path: string,
  init: RequestInit = {},
  options: RequestOptions = {},
): Promise<T> {
  const { redirectOnUnauthorized = true } = options;
```

```ts
if (
  redirectOnUnauthorized &&
  typeof window !== 'undefined' &&
  !window.location.pathname.startsWith('/login')
) {
```

Add to `apiClient`:

```ts
requestPasswordReset(body: PasswordResetRequest): Promise<PasswordResetResponse> {
  return request<PasswordResetResponse>(
    '/auth/password-reset',
    { method: 'POST', body: JSON.stringify(body) },
    { redirectOnUnauthorized: false },
  );
},
confirmPasswordReset(body: PasswordResetConfirmRequest): Promise<void> {
  return request<void>(
    '/auth/password-reset/confirm',
    { method: 'POST', body: JSON.stringify(body) },
    { redirectOnUnauthorized: false },
  );
},
```

- [ ] **Step 8: Run targeted frontend checks**

```bash
npm test -- src/lib/apiClient.test.ts
npm run typecheck
npm run lint
```

Expected: API client tests, TypeScript, and ESLint pass.

- [ ] **Step 9: Commit**

```bash
git add frontend/package.json frontend/package-lock.json frontend/vite.config.ts \
  frontend/tsconfig.app.json frontend/src/test/server.ts frontend/src/test/setup.ts \
  frontend/src/lib/types.ts frontend/src/lib/apiClient.ts \
  frontend/src/lib/apiClient.test.ts
git commit -m "test(frontend): add reset API test seam"
```

---

### Task 7: Build the public reset page test-first

**Files:**
- Create: `frontend/src/test/renderPasswordReset.tsx`
- Create: `frontend/src/pages/PasswordResetPage.tsx`
- Create: `frontend/src/pages/PasswordResetPage.test.tsx`
- Modify: `frontend/src/routes/index.tsx:1-36`
- Modify: `frontend/src/styles.css:91-142`

- [ ] **Step 1: Export route objects and add the public route**

Refactor `routes/index.tsx` to import `RouteObject`, import `PasswordResetPage`, export the array, and build the browser router from it:

```tsx
import { Navigate, createBrowserRouter, type RouteObject } from 'react-router-dom';

import { AppShell } from '@/components/AppShell';
import { RequireAuth } from '@/components/RequireAuth';
import { HomePage } from '@/pages/HomePage';
import { IngestionConfigurationsPage } from '@/pages/IngestionConfigurationsPage';
import { LoginPage } from '@/pages/LoginPage';
import { NewTicketPage } from '@/pages/NewTicketPage';
import { PasswordResetPage } from '@/pages/PasswordResetPage';
import { TicketDetailPage } from '@/pages/TicketDetailPage';
import { TicketListPage } from '@/pages/TicketListPage';
import { UserAdminPage } from '@/pages/UserAdminPage';

export const routes: RouteObject[] = [
  { path: '/login', element: <LoginPage /> },
  { path: '/password-reset', element: <PasswordResetPage /> },
  {
    element: <RequireAuth />,
    children: [
      {
        path: '/',
        element: <AppShell />,
        children: [
          { index: true, element: <HomePage /> },
          { path: 'tickets', element: <TicketListPage /> },
          { path: 'tickets/new', element: <NewTicketPage /> },
          { path: 'tickets/:id', element: <TicketDetailPage /> },
          { path: 'ingestion-configurations', element: <IngestionConfigurationsPage /> },
          {
            element: <RequireAuth adminOnly />,
            children: [{ path: 'admin/users', element: <UserAdminPage /> }],
          },
          { path: '*', element: <Navigate to="/" replace /> },
        ],
      },
    ],
  },
];

export const router = createBrowserRouter(routes);
```

- [ ] **Step 2: Add the route-aware render helper**

Create `renderPasswordReset.tsx`:

```tsx
import { render } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { createMemoryRouter, RouterProvider } from 'react-router-dom';

import { routes } from '@/routes';

export function renderPasswordReset(initialEntry = '/password-reset') {
  const router = createMemoryRouter(routes, { initialEntries: [initialEntry] });
  const user = userEvent.setup();
  const result = render(<RouterProvider router={router} />);
  return { ...result, router, user };
}
```

- [ ] **Step 3: Write rendered-flow acceptance tests**

Create `PasswordResetPage.test.tsx`:

```tsx
import { screen } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';

import { renderPasswordReset } from '@/test/renderPasswordReset';
import { server } from '@/test/server';

describe('PasswordResetPage', () => {
  it('requests a token and prefills confirmation', async () => {
    server.use(
      http.post('/api/v1/auth/password-reset', () =>
        HttpResponse.json({ token: 'returned-token' })),
    );
    const { user } = renderPasswordReset();

    await user.type(screen.getByLabelText('Email'), 'alex@example.com');
    await user.click(screen.getByRole('button', { name: 'Continue' }));

    expect(await screen.findByRole('heading', { name: 'Choose a new password' }))
      .toBeInTheDocument();
    expect(screen.getByLabelText('Reset token')).toHaveValue('returned-token');
  });

  it('opens confirmation from a token query parameter', () => {
    renderPasswordReset('/password-reset?token=from-email');

    expect(screen.getByRole('heading', { name: 'Choose a new password' }))
      .toBeInTheDocument();
    expect(screen.getByLabelText('Reset token')).toHaveValue('from-email');
  });

  it('does not submit mismatched passwords', async () => {
    let confirmationRequests = 0;
    server.use(
      http.post('/api/v1/auth/password-reset/confirm', () => {
        confirmationRequests += 1;
        return new HttpResponse(null, { status: 204 });
      }),
    );
    const { user } = renderPasswordReset('/password-reset?token=token');

    await user.type(screen.getByLabelText('New password'), 'new-password');
    await user.type(screen.getByLabelText('Confirm new password'), 'different-password');
    await user.click(screen.getByRole('button', { name: 'Reset password' }));

    expect(screen.getByRole('alert')).toHaveTextContent('Passwords do not match.');
    expect(confirmationRequests).toBe(0);
  });

  it('confirms and preserves next on the sign-in action', async () => {
    server.use(
      http.post('/api/v1/auth/password-reset/confirm', () =>
        new HttpResponse(null, { status: 204 })),
    );
    const { user } = renderPasswordReset(
      '/password-reset?token=token&next=%2Ftickets',
    );

    await user.type(screen.getByLabelText('New password'), 'new-password');
    await user.type(screen.getByLabelText('Confirm new password'), 'new-password');
    await user.click(screen.getByRole('button', { name: 'Reset password' }));

    expect(await screen.findByRole('heading', { name: 'Password updated' }))
      .toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Go to sign in' }))
      .toHaveAttribute('href', '/login?next=%2Ftickets');
  });

  it('shows an expired-token error without leaving the public route', async () => {
    server.use(
      http.post('/api/v1/auth/password-reset/confirm', () =>
        new HttpResponse(null, { status: 410 })),
    );
    const { router, user } = renderPasswordReset('/password-reset?token=expired');

    await user.type(screen.getByLabelText('New password'), 'new-password');
    await user.type(screen.getByLabelText('Confirm new password'), 'new-password');
    await user.click(screen.getByRole('button', { name: 'Reset password' }));

    expect(await screen.findByRole('alert'))
      .toHaveTextContent('This reset token has expired.');
    expect(router.state.location.pathname).toBe('/password-reset');
  });
});
```

- [ ] **Step 4: Run the page test and confirm RED**

```bash
npm test -- src/pages/PasswordResetPage.test.tsx
```

Expected: compilation fails because `PasswordResetPage` does not exist.

- [ ] **Step 5: Implement the page**

Create `PasswordResetPage.tsx`:

```tsx
import { useState, type FormEvent } from 'react';
import { Link, useSearchParams } from 'react-router-dom';

import { ApiError, apiClient } from '@/lib/apiClient';

type Phase = 'request' | 'confirm' | 'success';

const PASSWORD_MIN = 8;
const PASSWORD_MAX = 255;

export function PasswordResetPage() {
  const [searchParams] = useSearchParams();
  const tokenFromQuery = searchParams.get('token') ?? '';
  const nextPath = searchParams.get('next');
  const [phase, setPhase] = useState<Phase>(tokenFromQuery ? 'confirm' : 'request');
  const [email, setEmail] = useState('');
  const [token, setToken] = useState(tokenFromQuery);
  const [newPassword, setNewPassword] = useState('');
  const [confirmedPassword, setConfirmedPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const loginPath = nextPath
    ? `/login?next=${encodeURIComponent(nextPath)}`
    : '/login';

  async function requestReset(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(null);
    if (!email.trim()) {
      setError('Email is required.');
      return;
    }
    setSubmitting(true);
    try {
      const response = await apiClient.requestPasswordReset({ email: email.trim() });
      setToken(response.token);
      setPhase('confirm');
    } catch (caught) {
      setError(errorMessage(caught));
    } finally {
      setSubmitting(false);
    }
  }

  async function confirmReset(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(null);
    if (!token.trim()) {
      setError('Reset token is required.');
      return;
    }
    if (newPassword.length < PASSWORD_MIN || newPassword.length > PASSWORD_MAX) {
      setError(`Password must be ${PASSWORD_MIN}–${PASSWORD_MAX} characters.`);
      return;
    }
    if (newPassword !== confirmedPassword) {
      setError('Passwords do not match.');
      return;
    }
    setSubmitting(true);
    try {
      await apiClient.confirmPasswordReset({
        token: token.trim(),
        new_password: newPassword,
      });
      setPhase('success');
    } catch (caught) {
      setError(errorMessage(caught));
    } finally {
      setSubmitting(false);
    }
  }

  if (phase === 'success') {
    return (
      <section className="password-reset-page password-reset-success">
        <h2>Password updated</h2>
        <p>You can now sign in with your new password.</p>
        <Link to={loginPath}>Go to sign in</Link>
      </section>
    );
  }

  if (phase === 'request') {
    return (
      <section className="password-reset-page">
        <h2>Reset your password</h2>
        <p>Enter your email address to create a one-time reset token.</p>
        <form onSubmit={requestReset} noValidate>
          <label>
            Email
            <input
              type="email"
              name="email"
              autoComplete="username"
              value={email}
              onChange={(event) => setEmail(event.target.value)}
              required
            />
          </label>
          {error && <p className="login-error" role="alert">{error}</p>}
          <button type="submit" disabled={submitting}>
            {submitting ? 'Continuing…' : 'Continue'}
          </button>
        </form>
        <p className="password-reset-link"><Link to={loginPath}>Back to sign in</Link></p>
      </section>
    );
  }

  return (
    <section className="password-reset-page">
      <h2>Choose a new password</h2>
      <form onSubmit={confirmReset} noValidate>
        <label>
          Reset token
          <input
            name="token"
            value={token}
            onChange={(event) => setToken(event.target.value)}
            required
          />
        </label>
        <label>
          New password
          <input
            type="password"
            name="new_password"
            autoComplete="new-password"
            value={newPassword}
            onChange={(event) => setNewPassword(event.target.value)}
            required
            minLength={PASSWORD_MIN}
            maxLength={PASSWORD_MAX}
          />
        </label>
        <label>
          Confirm new password
          <input
            type="password"
            name="confirmed_password"
            autoComplete="new-password"
            value={confirmedPassword}
            onChange={(event) => setConfirmedPassword(event.target.value)}
            required
            minLength={PASSWORD_MIN}
            maxLength={PASSWORD_MAX}
          />
        </label>
        {error && <p className="login-error" role="alert">{error}</p>}
        <button type="submit" disabled={submitting}>
          {submitting ? 'Resetting…' : 'Reset password'}
        </button>
      </form>
      <p className="password-reset-link"><Link to="/password-reset">Request another token</Link></p>
    </section>
  );
}

function errorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.status === 410) {
      return 'This reset token has expired.';
    }
    if (error.status === 409) {
      return 'This reset token has already been used.';
    }
    if (error.status === 400) {
      return 'The reset token is invalid.';
    }
  }
  return 'Unable to reset the password. Try again.';
}
```

- [ ] **Step 6: Add reset-page styling**

Extend the existing login selectors so both public forms share the same card and form controls. Replace `.login-page`, `.login-page form`, `.login-page label`, `.login-page input`, `.login-page button`, and `.login-page button:disabled` selectors with these grouped equivalents:

```css
.login-page,
.password-reset-page {
  max-width: 360px;
  margin: 64px auto;
  padding: 24px;
  background: #fff;
  border: 1px solid var(--color-border);
  border-radius: 8px;
}

.login-page form,
.password-reset-page form {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.login-page label,
.password-reset-page label {
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 12px;
  color: var(--color-muted);
}

.login-page input,
.password-reset-page input {
  padding: 8px 10px;
  border: 1px solid var(--color-border);
  border-radius: 6px;
  font-size: 14px;
}

.login-page button,
.password-reset-page button {
  background: var(--color-accent);
  color: #fff;
  border: none;
  border-radius: 6px;
  padding: 8px 12px;
  font-size: 14px;
  cursor: pointer;
}

.login-page button:disabled,
.password-reset-page button:disabled {
  background: #93c1ec;
  cursor: progress;
}

.password-reset-link {
  margin: 12px 0 0;
  font-size: 12px;
}

.password-reset-link a,
.password-reset-success a {
  color: var(--color-accent);
}
```

- [ ] **Step 7: Run targeted frontend checks**

```bash
npm test -- src/pages/PasswordResetPage.test.tsx
npm run typecheck
npm run lint
```

Expected: page tests, TypeScript, and ESLint pass.

- [ ] **Step 8: Commit**

```bash
git add frontend/src/routes/index.tsx frontend/src/test/renderPasswordReset.tsx \
  frontend/src/pages/PasswordResetPage.tsx \
  frontend/src/pages/PasswordResetPage.test.tsx frontend/src/styles.css
git commit -m "feat(frontend): add password reset page"
```

---

### Task 8: Add login discoverability and CI coverage

**Files:**
- Create: `frontend/src/pages/LoginPage.test.tsx`
- Modify: `frontend/src/pages/LoginPage.tsx:1-63`
- Modify: `.github/workflows/ci.yml:44-63`

- [ ] **Step 1: Write the forgot-password link tests**

Create `LoginPage.test.tsx`:

```tsx
import { render, screen } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { createMemoryRouter, RouterProvider } from 'react-router-dom';
import { describe, expect, it } from 'vitest';

import { AuthProvider } from '@/lib/auth';
import { routes } from '@/routes';
import { server } from '@/test/server';

function renderLogin(initialEntry: string) {
  server.use(
    http.get('/api/v1/me', () => new HttpResponse(null, { status: 401 })),
  );
  const router = createMemoryRouter(routes, { initialEntries: [initialEntry] });
  render(
    <AuthProvider>
      <RouterProvider router={router} />
    </AuthProvider>,
  );
}

describe('LoginPage password reset link', () => {
  it('links to password reset', () => {
    renderLogin('/login');

    expect(screen.getByRole('link', { name: 'Forgot password?' }))
      .toHaveAttribute('href', '/password-reset');
  });

  it('preserves next in the reset link', () => {
    renderLogin('/login?next=%2Ftickets');

    expect(screen.getByRole('link', { name: 'Forgot password?' }))
      .toHaveAttribute('href', '/password-reset?next=%2Ftickets');
  });
});
```

- [ ] **Step 2: Run the login test and confirm RED**

```bash
npm test -- src/pages/LoginPage.test.tsx
```

Expected: no “Forgot password?” link is found.

- [ ] **Step 3: Add the login link**

Import `Link` beside the existing router imports, compute the reset path after `nextPath`, and append the link after the form:

```tsx
const passwordResetPath = searchParams.get('next')
  ? `/password-reset?next=${encodeURIComponent(nextPath)}`
  : '/password-reset';
```

```tsx
<p className="password-reset-link">
  <Link to={passwordResetPath}>Forgot password?</Link>
</p>
```

- [ ] **Step 4: Add frontend tests to CI**

Insert between lint and build in `.github/workflows/ci.yml`:

```yaml
      - name: Test
        run: npm test
      - name: Typecheck
        run: npm run typecheck
```

Rename the frontend job to `Frontend build, test & lint`.

- [ ] **Step 5: Run all focused frontend checks**

```bash
npm test -- src/pages/LoginPage.test.tsx
npm test -- src/pages/PasswordResetPage.test.tsx
npm test -- src/lib/apiClient.test.ts
npm run typecheck
npm run lint
npm run build
```

Expected: all targeted tests and static/build checks pass.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/pages/LoginPage.tsx frontend/src/pages/LoginPage.test.tsx \
  .github/workflows/ci.yml
git commit -m "feat(frontend): link login to password reset"
```

---

### Task 9: Run complete verification and review

**Files:**
- Modify only files required to fix verification or review findings.

- [ ] **Step 1: Run full backend verification**

From `backend/`:

```bash
./gradlew --no-daemon build
```

Expected: `BUILD SUCCESSFUL`, including all unit, repository, integration, Liquibase, and application-context tests.

- [ ] **Step 2: Run full frontend verification**

From `frontend/`:

```bash
npm test
npm run typecheck
npm run lint
npm run build
```

Expected: all tests pass, typecheck and lint exit zero, and Vite produces `dist/`.

- [ ] **Step 3: Inspect repository hygiene**

From the repository root:

```bash
git diff --check
git status --short
git diff --stat 719450f..HEAD
```

Expected: no whitespace errors; only issue #4, design, plan, test-harness, and CI files are present.

- [ ] **Step 4: Run the required code review**

Invoke the `code-review` skill against the implementation baseline `38ff97f`, covering both repository standards and issue #4/spec compliance. Fix every valid high- or medium-severity finding, rerun the affected focused test, then rerun the complete backend and frontend verification commands.

- [ ] **Step 5: Commit review fixes and the plan if still uncommitted**

Inspect `git status`, `git diff`, and `git log --oneline -10`; stage only intended files. If review produced changes, commit them with a concise Conventional Commit message describing the fix. Include this plan in the final intended commit if it has not already been committed.

- [ ] **Step 6: Confirm final state**

```bash
git status --short --branch
git log --oneline -12
```

Expected: working tree clean; current branch contains the design, implementation plan, implementation, tests, review fixes if any, and no unrelated files.
