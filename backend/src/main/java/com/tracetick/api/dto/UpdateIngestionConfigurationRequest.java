package com.tracetick.api.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.tracetick.domain.Severity;
import jakarta.validation.constraints.Size;

import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record UpdateIngestionConfigurationRequest(
        @Size(max = 255) String name,
        Severity defaultSeverity,
        Long defaultAssigneeUserId,
        Map<String, String> defaultTags,
        Boolean active,
        // Issue #10 phrased the field as `rotate_secret: true` in the acceptance criteria;
        // we accept both that literal snake_case form AND the camelCase Java-field name on
        // the way in. The response DTO keeps the camelCase form to stay consistent with the
        // rest of the TraceTick API surface.
        @JsonProperty("rotate_secret") @JsonAlias("rotateSecret")
        Boolean rotateSecret) {
}
