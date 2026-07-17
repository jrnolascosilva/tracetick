package com.tracetick.auth;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Foundation-ticket security wiring: permits all requests, disables CSRF and HTTP Basic, and
 * runs without sessions.
 *
 * <p>T2 ("Auth + User admin") replaces this bean with a real session-cookie authentication
 * filter chain backed by the {@code users} table. Until then the application has no auth.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain permitAllSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll())
                .csrf(csrf -> csrf.disable())
                .httpBasic(httpBasic -> httpBasic.disable())
                .formLogin(form -> form.disable())
                .logout(logout -> logout.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        return http.build();
    }
}