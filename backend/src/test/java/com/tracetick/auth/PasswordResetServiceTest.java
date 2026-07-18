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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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

    private static User activeUser() {
        return User.create(Customer.create("TraceTick", "ops@tracetick.local"),
                "alex@tracetick.local", "old-hash", Role.TECHNICIAN);
    }
}
