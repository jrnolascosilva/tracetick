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
