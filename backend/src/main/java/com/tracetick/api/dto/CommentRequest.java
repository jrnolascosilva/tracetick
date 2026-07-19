package com.tracetick.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CommentRequest(@NotBlank @Size(max = 65535) String body) {
}
