package com.tracetick.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class TagTest {

    @Test
    void ofBuildsAnImmutableKeyValuePair() {
        Tag tag = Tag.of("service", "api");

        assertThat(tag.getKey()).isEqualTo("service");
        assertThat(tag.getValue()).isEqualTo("api");
    }

    @ParameterizedTest
    @ValueSource(strings = {"service", "env", "team_platform", "service-1", "x", "0123456789abcdef0123456789abcdef"})
    void ofAcceptsValidKeys(String key) {
        assertThat(Tag.of(key, "v").getKey()).isEqualTo(key);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "with space", "with.dot", "UPPER:colon", "a/b", "with$dollar"})
    void ofRejectsInvalidKeys(String key) {
        assertThatExceptionOfType(InvalidTagException.class)
                .isThrownBy(() -> Tag.of(key, "v"));
    }

    @Test
    void ofRejectsKeyLongerThan32Characters() {
        String tooLong = "a".repeat(33);
        assertThatExceptionOfType(InvalidTagException.class)
                .isThrownBy(() -> Tag.of(tooLong, "v"));
    }

    @Test
    void ofAccepts32CharacterKey() {
        String exactly32 = "a".repeat(32);
        assertThat(Tag.of(exactly32, "v").getKey()).isEqualTo(exactly32);
    }

    @Test
    void ofRejectsNullKeyOrValue() {
        assertThatExceptionOfType(InvalidTagException.class)
                .isThrownBy(() -> Tag.of(null, "v"));
        assertThatExceptionOfType(InvalidTagException.class)
                .isThrownBy(() -> Tag.of("k", null));
    }

    @Test
    void ofRejectsValueLongerThan256Characters() {
        String tooLong = "v".repeat(257);
        assertThatExceptionOfType(InvalidTagException.class)
                .isThrownBy(() -> Tag.of("k", tooLong));
    }

    @Test
    void ofAccepts256CharacterValue() {
        String exactly256 = "v".repeat(256);
        assertThat(Tag.of("k", exactly256).getValue()).isEqualTo(exactly256);
    }

    @Test
    void ofAcceptsEmptyValue() {
        assertThat(Tag.of("k", "").getValue()).isEmpty();
    }
}
