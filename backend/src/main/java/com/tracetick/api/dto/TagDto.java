package com.tracetick.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TagDto(
        @NotBlank @Size(max = 32) String key,
        @NotBlank @Size(max = 256) String value) {
}
