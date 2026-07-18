package com.tracetick.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "tracetick.auth.password-reset")
public record PasswordResetProperties(Duration tokenTtl) {
}
