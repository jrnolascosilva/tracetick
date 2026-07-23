package com.tracetick.ingest;

import com.tracetick.domain.Severity;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SeverityExtractorTest {

    private static final Severity DEFAULT = Severity.MEDIUM;

    @Test
    void mapsCriticalAndFatalToCritical() {
        assertThat(SeverityExtractor.extract("critical", DEFAULT)).isEqualTo(Severity.CRITICAL);
        assertThat(SeverityExtractor.extract("CRITICAL", DEFAULT)).isEqualTo(Severity.CRITICAL);
        assertThat(SeverityExtractor.extract("Fatal", DEFAULT)).isEqualTo(Severity.CRITICAL);
    }

    @Test
    void mapsWarningAndHighToHigh() {
        assertThat(SeverityExtractor.extract("warning", DEFAULT)).isEqualTo(Severity.HIGH);
        assertThat(SeverityExtractor.extract("WARN", DEFAULT)).isEqualTo(Severity.HIGH);
        assertThat(SeverityExtractor.extract("high", DEFAULT)).isEqualTo(Severity.HIGH);
    }

    @Test
    void mapsInfoAndMediumToMedium() {
        assertThat(SeverityExtractor.extract("info", DEFAULT)).isEqualTo(Severity.MEDIUM);
        assertThat(SeverityExtractor.extract("medium", DEFAULT)).isEqualTo(Severity.MEDIUM);
    }

    @Test
    void mapsDebugAndLowToLow() {
        assertThat(SeverityExtractor.extract("debug", DEFAULT)).isEqualTo(Severity.LOW);
        assertThat(SeverityExtractor.extract("low", DEFAULT)).isEqualTo(Severity.LOW);
    }

    @Test
    void fallsBackToDefaultWhenValueIsNull() {
        assertThat(SeverityExtractor.extract(null, Severity.HIGH)).isEqualTo(Severity.HIGH);
        assertThat(SeverityExtractor.extract(null, Severity.LOW)).isEqualTo(Severity.LOW);
    }

    @Test
    void fallsBackToDefaultWhenValueIsBlank() {
        assertThat(SeverityExtractor.extract("   ", DEFAULT)).isEqualTo(DEFAULT);
    }

    @Test
    void fallsBackToDefaultWhenValueIsUnrecognized() {
        assertThat(SeverityExtractor.extract("urgent", DEFAULT)).isEqualTo(DEFAULT);
        assertThat(SeverityExtractor.extract("p1", Severity.CRITICAL)).isEqualTo(Severity.CRITICAL);
    }

    @Test
    void trimsWhitespaceBeforeMatching() {
        assertThat(SeverityExtractor.extract("  critical  ", DEFAULT)).isEqualTo(Severity.CRITICAL);
    }
}
