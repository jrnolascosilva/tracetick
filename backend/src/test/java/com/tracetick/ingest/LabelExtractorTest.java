package com.tracetick.ingest;

import com.tracetick.domain.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LabelExtractorTest {

    @Test
    void returnsEmptyListWhenPayloadLabelsIsNull() {
        assertThat(LabelExtractor.extract(null)).isEmpty();
    }

    @Test
    void returnsEmptyListWhenPayloadLabelsIsEmpty() {
        assertThat(LabelExtractor.extract(Map.of())).isEmpty();
    }

    @Test
    void convertsEachEntryToATag() {
        List<Tag> tags = LabelExtractor.extract(Map.of("service", "api", "env", "prod"));

        assertThat(tags).extracting(Tag::getKey, Tag::getValue)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("service", "api"),
                        org.assertj.core.groups.Tuple.tuple("env", "prod"));
    }

    @Test
    void rejectsKeysThatDoNotMatchTheTagKeyPattern() {
        assertThatThrownBy(() -> LabelExtractor.extract(Map.of("Bad Key", "value")))
                .isInstanceOf(com.tracetick.domain.InvalidTagException.class);
    }

    @Test
    void rejectsValuesLongerThan256Chars() {
        String tooLong = "v".repeat(257);
        assertThatThrownBy(() -> LabelExtractor.extract(Map.of("service", tooLong)))
                .isInstanceOf(com.tracetick.domain.InvalidTagException.class);
    }

    @Test
    void acceptsValuesUpTo256Chars() {
        String exactlyMax = "v".repeat(256);
        List<Tag> tags = LabelExtractor.extract(Map.of("service", exactlyMax));
        assertThat(tags).hasSize(1);
        assertThat(tags.get(0).getValue()).isEqualTo(exactlyMax);
    }

    @Test
    void rejectsNullValues() {
        assertThatThrownBy(() -> LabelExtractor.extract(java.util.Collections.singletonMap("service", null)))
                .isInstanceOf(com.tracetick.domain.InvalidTagException.class);
    }

    @Test
    void rejectsBlankKeys() {
        assertThatThrownBy(() -> LabelExtractor.extract(Map.of("", "value")))
                .isInstanceOf(com.tracetick.domain.InvalidTagException.class);
    }
}
