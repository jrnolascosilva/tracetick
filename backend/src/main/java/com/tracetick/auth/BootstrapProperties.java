package com.tracetick.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "tracetick.bootstrap")
public record BootstrapProperties(boolean enabled, String adminEmail, String adminPassword) {
}
