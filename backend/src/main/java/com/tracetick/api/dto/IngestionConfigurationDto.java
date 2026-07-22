package com.tracetick.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.tracetick.domain.IngestionConfiguration;
import com.tracetick.domain.Severity;

import java.time.Instant;
import java.util.Map;

/**
 * <p>{@code hmacSecret} is included ONLY when the response is from a request that just
 * generated or rotated the secret. It is otherwise omitted by Jackson's
 * {@code @JsonInclude(NON_NULL)} — see {@code IngestionConfigurationDtoTest} for the
 * no-leak guarantee.</p>
 */
@JsonInclude(Include.NON_NULL)
public record IngestionConfigurationDto(
        Long id,
        String name,
        String urlToken,
        String webhookUrl,
        Severity defaultSeverity,
        Long defaultAssigneeUserId,
        Map<String, String> defaultTags,
        boolean active,
        Instant createdAt,
        String hmacSecret) {

    private static final String WEBHOOK_PATH_PREFIX = "/api/v1/ingest/";

    public static IngestionConfigurationDto from(IngestionConfiguration config) {
        return build(config, null);
    }

    /**
     * Returns the DTO including the HMAC secret. Use ONLY for the response of a request
     * that just generated or rotated the secret — the secret is otherwise write-only.
     */
    public static IngestionConfigurationDto exposingSecret(IngestionConfiguration config) {
        return build(config, config.getHmacSecret());
    }

    private static IngestionConfigurationDto build(IngestionConfiguration config, String secretToExpose) {
        return new IngestionConfigurationDto(
                config.getId(),
                config.getName(),
                config.getUrlToken(),
                WEBHOOK_PATH_PREFIX + config.getUrlToken(),
                config.getDefaultSeverity(),
                config.getDefaultAssignee() == null ? null : config.getDefaultAssignee().getId(),
                config.getDefaultTags(),
                config.isActive(),
                config.getCreatedAt(),
                secretToExpose);
    }
}
