package com.tracetick.api.dto;

import com.tracetick.domain.Role;
import com.tracetick.domain.User;

public record UserDto(
        Long id,
        String email,
        Role role,
        boolean active) {

    public static UserDto from(User user) {
        return new UserDto(user.getId(), user.getEmail(), user.getRole(), user.isActive());
    }
}
