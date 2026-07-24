package com.tracetick.api;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI metadata for the TraceTick HTTP API.
 *
 * <p>The reflection-based metadata for each endpoint comes from {@code springdoc-openapi}
 * scanning the controllers under {@code com.tracetick.api/**} and {@code com.tracetick.ingest}.
 * This class only carries the top-level {@link OpenAPIDefinition} so the generated document
 * has a stable title, version, and description.
 */
@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "TraceTick API",
                version = "0.1.0-SNAPSHOT",
                description = "HTTP API for the TraceTick support and incident tracking system. "
                        + "Session-based authentication via httpOnly cookies; tickets are created "
                        + "either manually by Users or automatically by authenticated webhook "
                        + "ingestion from external monitoring sources.",
                contact = @Contact(name = "TraceTick", email = "ops@tracetick.local"),
                license = @License(name = "TBD")
        )
)
public class OpenApiConfig {
}
