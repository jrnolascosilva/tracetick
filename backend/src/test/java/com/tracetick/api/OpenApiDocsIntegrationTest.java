package com.tracetick.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
class OpenApiDocsIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("tracetick")
            .withUsername("tracetick")
            .withPassword("tracetick");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void apiDocsEndpointReturnsValidOpenApi3Document() throws Exception {
        MvcResult result = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn();

        String contentType = result.getResponse().getContentType();
        assertThat(contentType).isNotNull();
        assertThat(MediaType.valueOf(contentType).isCompatibleWith(MediaType.APPLICATION_JSON)).isTrue();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.get("openapi"))
                .as("the document declares an OpenAPI version")
                .isNotNull();
        assertThat(body.get("openapi").asText())
                .as("the OpenAPI version is a 3.x release")
                .startsWith("3.");

        JsonNode info = body.get("info");
        assertThat(info).as("info block is present").isNotNull();
        assertThat(info.get("title").asText())
                .as("info.title is set")
                .isNotBlank();
        assertThat(info.get("version").asText())
                .as("info.version is set")
                .isNotBlank();
        assertThat(info.get("description").asText())
                .as("info.description is set")
                .isNotBlank();
    }

    @Test
    void apiDocsIncludesAllRestControllers() throws Exception {
        MvcResult result = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        JsonNode paths = body.get("paths");
        assertThat(paths)
                .as("paths block is present")
                .isNotNull();

        // At least one path from each of the major controllers must be discovered
        // through reflection — no hand-written annotations required.
        assertThat(paths.has("/api/v1/tickets"))
                .as("tickets list endpoint is documented")
                .isTrue();
        assertThat(paths.has("/api/v1/auth/login"))
                .as("auth login endpoint is documented")
                .isTrue();
        assertThat(paths.has("/api/v1/ingest/{urlToken}"))
                .as("webhook ingest endpoint is documented")
                .isTrue();
    }

    @Test
    void swaggerUiHtmlRedirectsToInteractiveUi() throws Exception {
        mockMvc.perform(get("/swagger-ui.html"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void swaggerUiIndexServesInteractiveHtml() throws Exception {
        MvcResult result = mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk())
                .andReturn();

        String contentType = result.getResponse().getContentType();
        assertThat(contentType).isNotNull();
        assertThat(MediaType.valueOf(contentType).isCompatibleWith(MediaType.TEXT_HTML)).isTrue();

        String html = result.getResponse().getContentAsString();
        assertThat(html)
                .as("Swagger UI HTML page references the swagger-ui bundle")
                .containsIgnoringCase("swagger-ui");
    }
}
