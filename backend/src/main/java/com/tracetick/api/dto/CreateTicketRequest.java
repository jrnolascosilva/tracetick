package com.tracetick.api.dto;

import com.tracetick.domain.Severity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateTicketRequest(
        @NotBlank @Size(max = 255) String title,
        @NotBlank String description,
        Severity severity,
        List<TagInput> tags) {

    public CreateTicketRequest {
        if (tags == null) {
            tags = List.of();
        }
    }

    public record TagInput(@NotBlank @Size(max = 32) String key, @NotBlank @Size(max = 256) String value) {}
}
