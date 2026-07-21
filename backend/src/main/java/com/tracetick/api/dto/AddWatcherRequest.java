package com.tracetick.api.dto;

import jakarta.validation.constraints.NotNull;

public record AddWatcherRequest(@NotNull Long userId) {
}