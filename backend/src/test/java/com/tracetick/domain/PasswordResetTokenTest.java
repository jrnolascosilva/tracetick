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