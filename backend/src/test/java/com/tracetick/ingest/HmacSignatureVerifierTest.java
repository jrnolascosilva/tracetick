package com.tracetick.ingest;

import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HmacSignatureVerifierTest {

    private static final String SECRET = "test-secret-with-enough-entropy-to-avoid-short-key-issues-1234567890";

    @Test
    void acceptsSignatureComputedByTheStandardAlgorithm() {
        byte[] body = "{\"hello\":\"world\"}".getBytes(StandardCharsets.UTF_8);
        String header = "sha256=" + hex(hmac(SECRET, body));

        assertThat(HmacSignatureVerifier.verify(body, header, SECRET)).isTrue();
    }

    @Test
    void rejectsTamperedBody() {
        byte[] original = "{\"hello\":\"world\"}".getBytes(StandardCharsets.UTF_8);
        byte[] tampered = "{\"hello\":\"WORLD\"}".getBytes(StandardCharsets.UTF_8);
        String header = "sha256=" + hex(hmac(SECRET, original));

        assertThat(HmacSignatureVerifier.verify(tampered, header, SECRET)).isFalse();
    }

    @Test
    void rejectsTamperedSecret() {
        byte[] body = "{\"hello\":\"world\"}".getBytes(StandardCharsets.UTF_8);
        String header = "sha256=" + hex(hmac(SECRET, body));

        assertThat(HmacSignatureVerifier.verify(body, header, "different-secret")).isFalse();
    }

    @Test
    void rejectsMissingSha256Prefix() {
        byte[] body = "{\"hello\":\"world\"}".getBytes(StandardCharsets.UTF_8);
        String headerWithoutPrefix = hex(hmac(SECRET, body));

        assertThat(HmacSignatureVerifier.verify(body, headerWithoutPrefix, SECRET)).isFalse();
    }

    @Test
    void rejectsWrongAlgorithmPrefix() {
        byte[] body = "{\"hello\":\"world\"}".getBytes(StandardCharsets.UTF_8);
        String header = "sha1=" + hex(hmac(SECRET, body));

        assertThat(HmacSignatureVerifier.verify(body, header, SECRET)).isFalse();
    }

    @Test
    void rejectsNullHeader() {
        byte[] body = "{}".getBytes(StandardCharsets.UTF_8);

        assertThat(HmacSignatureVerifier.verify(body, null, SECRET)).isFalse();
    }

    @Test
    void rejectsBlankHeader() {
        byte[] body = "{}".getBytes(StandardCharsets.UTF_8);

        assertThat(HmacSignatureVerifier.verify(body, "   ", SECRET)).isFalse();
    }

    @Test
    void rejectsHexThatIsTooShort() {
        byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
        String header = "sha256=deadbeef";

        assertThat(HmacSignatureVerifier.verify(body, header, SECRET)).isFalse();
    }

    @Test
    void rejectsMalformedHex() {
        byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
        String header = "sha256=" + "zz".repeat(32);

        assertThat(HmacSignatureVerifier.verify(body, header, SECRET)).isFalse();
    }

    @Test
    void rejectsNullBody() {
        assertThatThrownBy(() -> HmacSignatureVerifier.verify(null, "sha256=abc", SECRET))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNullSecret() {
        assertThatThrownBy(() -> HmacSignatureVerifier.verify("body".getBytes(StandardCharsets.UTF_8),
                "sha256=abc", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static byte[] hmac(String secret, byte[] body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(body);
        } catch (NoSuchAlgorithmException | java.security.InvalidKeyException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
