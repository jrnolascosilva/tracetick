package com.tracetick.ingest;

import com.tracetick.domain.Severity;

import java.util.Map;

/**
 * Maps a webhook payload's {@code severity} string to a {@link Severity}.
 *
 * <p>Mapping is case-insensitive and whitespace-tolerant. Unknown or absent values fall
 * back to the {@code defaultSeverity} passed in (which the ingest pipeline reads from the
 * {@code IngestionConfiguration}). The four-tier mapping matches the spec — see T10
 * acceptance criteria and the ticket domain's {@link Severity} enum.
 */
public final class SeverityExtractor {

    private static final Map<String, Severity> MAPPING = Map.of(
            "critical", Severity.CRITICAL,
            "fatal", Severity.CRITICAL,
            "warning", Severity.HIGH,
            "warn", Severity.HIGH,
            "high", Severity.HIGH,
            "info", Severity.MEDIUM,
            "medium", Severity.MEDIUM,
            "debug", Severity.LOW,
            "low", Severity.LOW);

    private SeverityExtractor() {
    }

    public static Severity extract(String raw, Severity defaultSeverity) {
        if (raw == null) {
            return defaultSeverity;
        }
        String normalized = raw.trim().toLowerCase();
        if (normalized.isEmpty()) {
            return defaultSeverity;
        }
        Severity mapped = MAPPING.get(normalized);
        return mapped != null ? mapped : defaultSeverity;
    }
}
