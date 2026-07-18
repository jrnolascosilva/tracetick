package com.tracetick;

import com.tracetick.auth.BootstrapProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@EnableConfigurationProperties(BootstrapProperties.class)
public class TraceTickApplication {

    public static void main(String[] args) {
        SpringApplication.run(TraceTickApplication.class, args);
    }
}