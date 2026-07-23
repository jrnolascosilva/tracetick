package com.tracetick.ingest;

import com.tracetick.domain.Tag;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Extracts key-value {@link Tag}s from the {@code labels} object on a webhook payload.
 *
 * <p>Keys are validated against {@link Tag}'s key pattern ({@code [a-z0-9_-]{1,32}}); values
 * are validated against the {@code <= 256} char limit. Invalid keys/values surface as
 * {@link com.tracetick.domain.InvalidTagException} so the ingest pipeline can map that to a
 * 400-class response upstream. A null or empty map yields an empty tag list.
 */
public final class LabelExtractor {

    private LabelExtractor() {
    }

    public static List<Tag> extract(Map<String, String> labels) {
        if (labels == null || labels.isEmpty()) {
            return List.of();
        }
        List<Tag> tags = new ArrayList<>(labels.size());
        for (Map.Entry<String, String> entry : labels.entrySet()) {
            tags.add(Tag.of(entry.getKey(), entry.getValue()));
        }
        return List.copyOf(tags);
    }
}
