package com.tracetick.ingest;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Verifies the {@code X-TraceTick-Signature} header on inbound webhook payloads. The
 * signature scheme is fixed by ADR-0005: HMAC-SHA256 over the raw request body, hex
 * encoded, prefixed with {@code sha256=}.
 *
 * <p>{@link #verify(byte[], String, String)} returns {@code true} on a match and
 * {@code false} on any mismatch or malformed header — the ingest endpoint maps the latter
 * to a 401 response without leaking the failure reason to the caller. The comparison is
 * constant-time so an attacker cannot learn the prefix length from response timing.
 */
public final class HmacSignatureVerifier {

    private static final String HEADER_PREFIX = "sha256=";
    private static final String MAC_ALGORITHM = "HmacSHA256";
    private static final int SHA256_HEX_LENGTH = 64;

    private HmacSignatureVerifier() {
    }

    public static boolean verify(byte[] body, String header, String secret) {
        if (body == null) {
            throw new IllegalArgumentException("body must not be null");
        }
        if (secret == null || secret.isEmpty()) {
            throw new IllegalArgumentException("secret must not be null or empty");
        }
        if (header == null || header.isBlank()) {
            return false;
        }
        if (!header.startsWith(HEADER_PREFIX)) {
            return false;
        }
        String hex = header.substring(HEADER_PREFIX.length());
        if (hex.length() != SHA256_HEX_LENGTH) {
            return false;
        }
        byte[] provided;
        try {
            provided = decodeHex(hex);
        } catch (IllegalArgumentException ex) {
            return false;
        }
        byte[] expected = computeSignature(secret, body);
        return MessageDigest.isEqual(expected, provided);
    }

    private static byte[] computeSignature(String secret, byte[] body) {
        try {
            Mac mac = Mac.getInstance(MAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), MAC_ALGORITHM));
            return mac.doFinal(body);
        } catch (NoSuchAlgorithmException | java.security.InvalidKeyException e) {
            throw new IllegalStateException("HmacSHA256 unavailable", e);
        }
    }

    private static byte[] decodeHex(String hex) {
        if ((hex.length() & 1) != 0) {
            throw new IllegalArgumentException("hex string must have even length");
        }
        byte[] out = new byte[hex.length() / 2];
        for (int i = 0; i < out.length; i++) {
            int hi = Character.digit(hex.charAt(i * 2), 16);
            int lo = Character.digit(hex.charAt(i * 2 + 1), 16);
            if (hi < 0 || lo < 0) {
                throw new IllegalArgumentException("hex string contains non-hex characters");
            }
            out[i] = (byte) ((hi << 4) | lo);
        }
        return out;
    }
}
