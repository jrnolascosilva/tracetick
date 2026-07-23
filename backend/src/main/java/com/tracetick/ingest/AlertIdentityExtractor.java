package com.tracetick.ingest;

import java.util.Map;

/**
 * Pulls a stable per-incident identifier out of a webhook payload for use in the dedup
 * fingerprint. Returns {@code null} when the payload carries no usable identity — in which
 * case the ingest pipeline skips dedup and each fire creates a fresh Ticket.
 *
 * <p>Resolution order (first non-blank wins):
 * <ol>
 *   <li>{@code payload.alert_id} — common in Alertmanager/Prometheus webhooks.</li>
 *   <li>{@code payload.fingerprint} — used by PagerDuty and some Opsgenie payloads.</li>
 *   <li>{@code payload.alertname} joined with {@code payload.instance} by {@code @} —
 *       covers Grafana-style payloads where neither stable id is present.</li>
 * </ol>
 *
 * <p>Either component of the joined identity can be absent; only the present pieces are
 * used (so {@code alertname=HighCPU} alone resolves to {@code "HighCPU"}).
 */
public final class AlertIdentityExtractor {

    private static final String SEPARATOR = "@";

    private AlertIdentityExtractor() {
    }

    public static String extract(Map<String, Object> payload) {
        if (payload == null) {
            return null;
        }
        String alertId = stringOrNull(payload.get("alert_id"));
        if (alertId != null) {
            return alertId;
        }
        String fingerprint = stringOrNull(payload.get("fingerprint"));
        if (fingerprint != null) {
            return fingerprint;
        }
        String alertname = stringOrNull(payload.get("alertname"));
        String instance = stringOrNull(payload.get("instance"));
        if (alertname == null && instance == null) {
            return null;
        }
        if (alertname == null) {
            return instance;
        }
        if (instance == null) {
            return alertname;
        }
        return alertname + SEPARATOR + instance;
    }

    private static String stringOrNull(Object value) {
        if (value == null) {
            return null;
        }
        String s = value.toString();
        return s.isBlank() ? null : s;
    }
}
