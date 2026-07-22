package com.tracetick.api.dto;

import com.tracetick.domain.Severity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Map;

public record CreateIngestionConfigurationRequest(
        @NotBlank @Size(max = 255) String name,
        Severity defaultSeverity,
        Long defaultAssigneeUserId,
        Map<String, String> defaultTags) {
}
