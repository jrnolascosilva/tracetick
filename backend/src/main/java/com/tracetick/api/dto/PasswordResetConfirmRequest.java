package com.tracetick.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordResetConfirmRequest(
        @NotBlank @Size(max = 255) String token,
        @JsonProperty("new_password")
        @NotBlank @Size(min = 8, max = 255) String newPassword) {
}
