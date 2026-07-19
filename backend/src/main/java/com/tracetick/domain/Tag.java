package com.tracetick.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.util.regex.Pattern;

@Embeddable
@Getter
@EqualsAndHashCode
public class Tag {

    private static final Pattern VALID_KEY = Pattern.compile("[a-z0-9_-]{1,32}");
    private static final int MAX_VALUE_LENGTH = 256;

    @Column(name = "key", nullable = false, length = 32)
    private String key;

    @Column(name = "value", nullable = false, length = MAX_VALUE_LENGTH)
    private String value;

    protected Tag() {
    }

    private Tag(String key, String value) {
        this.key = key;
        this.value = value;
    }

    public static Tag of(String key, String value) {
        validateKey(key);
        validateValue(value);
        return new Tag(key, value);
    }

    private static void validateKey(String key) {
        if (key == null || !VALID_KEY.matcher(key).matches()) {
            throw new InvalidTagException("Invalid tag key: must match [a-z0-9_-]{1,32}");
        }
    }

    private static void validateValue(String value) {
        if (value == null) {
            throw new InvalidTagException("Invalid tag value: must not be null");
        }
        if (value.length() > MAX_VALUE_LENGTH) {
            throw new InvalidTagException("Invalid tag value: must be <= " + MAX_VALUE_LENGTH + " characters");
        }
    }
}
