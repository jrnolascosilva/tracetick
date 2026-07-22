package com.tracetick.ingest;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class IngestionTokenGeneratorTest {

    @Test
    void urlTokenUsesUrlSafeAlphabet() {
        for (int i = 0; i < 50; i++) {
            String token = IngestionTokenGenerator.urlToken();
            assertThat(token).matches("^[A-Za-z0-9_-]+$");
            assertThat(token).hasSizeGreaterThanOrEqualTo(16);
            assertThat(token).doesNotContain("=");
        }
    }

    @Test
    void hmacSecretUsesUrlSafeAlphabetAndIsLongerThanTheUrlToken() {
        for (int i = 0; i < 50; i++) {
            String secret = IngestionTokenGenerator.hmacSecret();
            assertThat(secret).matches("^[A-Za-z0-9_-]+$");
            assertThat(secret).hasSizeGreaterThanOrEqualTo(32);
        }
    }

    @Test
    void consecutiveCallsReturnDifferentTokens() {
        Set<String> tokens = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            tokens.add(IngestionTokenGenerator.urlToken());
        }
        assertThat(tokens).hasSize(100);
    }

    @Test
    void consecutiveCallsReturnDifferentSecrets() {
        Set<String> secrets = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            secrets.add(IngestionTokenGenerator.hmacSecret());
        }
        assertThat(secrets).hasSize(100);
    }
}
