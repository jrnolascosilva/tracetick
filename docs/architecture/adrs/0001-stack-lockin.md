# ADR-0001: Stack lock-in

- Status: Accepted
- Date: 2026-07-16
- Deciders: TraceTick maintainers

## Context

TraceTick is a self-hosted ticketing + incident-tracking application. The repository is greenfield
— no existing code, build tooling, or runtime to preserve. Before any feature ticket can be built
we must commit to a stack so that downstream tickets (auth, persistence, frontend, CI) have
stable targets.

The constraints shaping this decision are:

- Self-hostable by small developer teams; single-tenant per instance.
- Schema migrations must be reviewable and reproducible across deployments.
- The web UI must be fast on cold load and easy to iterate on without a backend rebuild.
- The team is comfortable with JVM and TypeScript ecosystems.

## Decision

TraceTick v1 is built on the following locked-in stack:

- **Backend**: Spring Boot 3.4 (Java 21), Gradle (Kotlin DSL) with the Gradle wrapper.
- **Database**: PostgreSQL 16, schema managed by Liquibase (XML/YAML changesets).
- **Frontend**: Vite + React 19 + TypeScript, with React Router for routing and TanStack
  React Query for server-state caching.
- **CI**: GitHub Actions. Backend job builds and tests against a Postgres service container.
  Frontend job installs, lints, type-checks, and builds.
- **Local development**: `docker compose` brings up Postgres; backend and frontend run from
  the developer's toolchain (no Docker-required for the apps themselves).

The stack is locked in for v1. Each later ticket assumes these choices and does not introduce
alternatives (e.g. no second UI framework, no MySQL, no Maven).

## Consequences

Positive:

- One canonical way to build and run the project removes onboarding friction.
- Spring Boot's actuator, validation, and security starters cover most of what each ticket
  needs; we add libraries, not infrastructure.
- Liquibase changesets land in version control and apply deterministically against any
  Postgres 16 instance.
- React Query gives us a uniform story for server-state, retries, and 401-driven redirects to
  `/login`.

Negative / risks:

- Lock-in to JVM tooling means future maintainers must be comfortable with Spring Boot. This
  is acceptable for v1; a future ADR can revisit if hiring or maintenance patterns change.
- Gradle Kotlin DSL is more verbose than Groovy DSL for trivial builds. We accept the tradeoff
  for type-safe build scripts.
- The frontend ships React Query by default; if a ticket requires fine-grained optimistic
  control it may need React Query's mutation hooks, not just query hooks.

## Notes

- The ADR is the single source of truth for the stack. CI workflows, README, and ticket
  acceptance criteria must agree with it.
- A future ADR may layer additional libraries (e.g. Flyway alternative, Next.js migration),
  but only by superseding this one explicitly.