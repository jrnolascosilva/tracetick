package com.tracetick.ingest;

/**
 * Generates URL-safe, cryptographically-random tokens for use as webhook URL segments and
 * HMAC secrets.
 *
 * <p>Both kinds of token are encoded as URL-safe Base64 without padding, the format that
 * ADR-0005 keeps as the carry-around value for {@code X-TraceTick-Signature}. Callers
 * specify the byte length; the encoded string is the same length rounded up to a 6-bit
 * boundary (~ceil(n * 8 / 6) characters, never padded).
 */
public final class IngestionTokenGenerator {

    private IngestionTokenGenerator() {
    }

    public static String urlToken() {
        return randomToken(18);
    }

    public static String hmacSecret() {
        return randomToken(32);
    }

    private static String randomToken(int byteLength) {
        byte[] bytes = new byte[byteLength];
        java.security.SecureRandom random = new java.security.SecureRandom();
        random.nextBytes(bytes);
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
