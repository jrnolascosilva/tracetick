package com.tracetick.ingest;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AlertIdentityExtractorTest {

    @Test
    void prefersAlertIdOverOtherFields() {
        String identity = AlertIdentityExtractor.extract(Map.of(
                "alert_id", "abc",
                "fingerprint", "should-not-be-used",
                "alertname", "should-not-be-used",
                "instance", "host1"));

        assertThat(identity).isEqualTo("abc");
    }

    @Test
    void fallsBackToFingerprintWhenAlertIdIsAbsent() {
        String identity = AlertIdentityExtractor.extract(Map.of(
                "fingerprint", "fp-123",
                "alertname", "should-not-be-used"));

        assertThat(identity).isEqualTo("fp-123");
    }

    @Test
    void composesAlertnameAndInstanceWhenNeitherAlertIdNorFingerprintIsPresent() {
        String identity = AlertIdentityExtractor.extract(Map.of(
                "alertname", "HighCPU",
                "instance", "host1"));

        assertThat(identity).isEqualTo("HighCPU@host1");
    }

    @Test
    void usesAlertnameAloneWhenInstanceIsAbsent() {
        String identity = AlertIdentityExtractor.extract(Map.of(
                "alertname", "HighCPU"));

        assertThat(identity).isEqualTo("HighCPU");
    }

    @Test
    void usesInstanceAloneWhenAlertnameIsAbsent() {
        String identity = AlertIdentityExtractor.extract(Map.of(
                "instance", "host1"));

        assertThat(identity).isEqualTo("host1");
    }

    @Test
    void returnsNullWhenNoIdentityFieldIsPresent() {
        assertThat(AlertIdentityExtractor.extract(Map.of("severity", "critical"))).isNull();
        assertThat(AlertIdentityExtractor.extract(Map.of())).isNull();
        assertThat(AlertIdentityExtractor.extract(null)).isNull();
    }

    @Test
    void ignoresBlankAlertIdAndFallsBackToFingerprint() {
        String identity = AlertIdentityExtractor.extract(Map.of(
                "alert_id", "   ",
                "fingerprint", "fp-1"));

        assertThat(identity).isEqualTo("fp-1");
    }

    @Test
    void ignoresBothBlankAndFallsBackToAlertname() {
        String identity = AlertIdentityExtractor.extract(Map.of(
                "alert_id", "",
                "fingerprint", "",
                "alertname", "HighCPU",
                "instance", "host1"));

        assertThat(identity).isEqualTo("HighCPU@host1");
    }
}
