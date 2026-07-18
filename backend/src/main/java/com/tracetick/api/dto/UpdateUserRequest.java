package com.tracetick.api.dto;

import com.tracetick.domain.Role;

public record UpdateUserRequest(
        Role role,
        Boolean active) {
}
