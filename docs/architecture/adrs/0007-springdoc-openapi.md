# 0007 — Springdoc-openapi for HTTP API documentation

TraceTick's HTTP API is auto-documented via `springdoc-openapi-starter-webmvc-ui`
(2.8.6). The library scans the Spring controllers at startup and emits an OpenAPI 3
JSON document at `/v3/api-docs`, plus an interactive Swagger UI at `/swagger-ui.html`,
with no annotations required on the controllers themselves. We extend the top-level
metadata via `@OpenAPIDefinition` on `OpenApiConfig` (title, version, description).

The two endpoints are reachable without auth in dev and in production. They expose the
public API surface — paths, methods, request/response schemas — but never user data or
Ticket content, so leaving them public is acceptable for v1. Locking them down (e.g.
behind the same session check as the rest of the API, or admin-only) is a separate
auth ticket.

Considered and rejected: (a) hand-written OpenAPI YAML checked into the repo (drifts
from the controllers the moment either side changes; we lose the single source of
truth); (b) `springfox` (deprecated, not maintained for Spring Boot 3.x); (c)
`swagger-core` directly without the springdoc starter (loses the Spring-aware scanning
and Swagger UI integration); (d) generating docs from the controllers via a custom
build-time tool (heavier than the project needs and adds a non-Gradle step).

Consequences: the springdoc dependency is added to the main classpath only — no test
artifact. No new test seam is needed: the existing `@SpringBootTest` slice already
boots the full web context, so the `/v3/api-docs` endpoint is reachable in tests.
`OpenApiDocsIntegrationTest` exercises the contract (200, OpenAPI 3.x, controllers
discovered, Swagger UI served). Future endpoints are documented automatically as
controllers are added — no per-ticket work required.
