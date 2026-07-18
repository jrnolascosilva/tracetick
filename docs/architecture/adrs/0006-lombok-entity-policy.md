# 0006 — Lombok for entities, getters-only with policy enforcement via review

We adopt Project Lombok on TraceTick's JPA entities to remove the
`getX()` boilerplate, but only as a `@Getter`-only pass. Setters, `@Data`,
`@Builder`, `@Value`, and any Lombok annotation that generates a public
constructor are forbidden on `@Entity` classes; the policy is enforced
through `docs/lombok-policy.md`, ADR-0006, the reference implementations
in `domain/User.java` and `domain/Customer.java`, and code review against
the tracking ticket (issue #T13). The reasoning is that the existing
aggregate style — `protected` no-args + `private` all-args +
`public static create(...)` + hand-written behavior methods like
`User.deactivate()` — already encodes the invariants Lombok shortcuts
would silently bypass; we are not willing to give that up.

Considered and rejected: (a) no Lombok at all (boilerplate cost outweighed
the safety we already have via review); (b) Lombok with `@Setter`
everywhere (re-introduces the setter surface the factory pattern
prevents); (c) Lombok with `@Builder` (lets callers set `id`, `active`,
`createdAt` — the invariants the `create()` factory guarantees); (d)
ArchUnit-driven automated enforcement (rejected for this ticket as
heavier than the project currently needs; recorded as a natural follow-up
in `docs/lombok-policy.md` if hard CI enforcement is later required).

Consequences: every future entity (Ticket, Tag, IngestionConfiguration,
Event, Comment, etc.) must follow the construction pattern in
`docs/lombok-policy.md`. Lombok version pinned at 1.18.34; both
`compileOnly` and `annotationProcessor` are declared on the main
classpath, neither on `test`. PRs that violate the policy are rejected at
review.
