# Lombok policy for JPA entities

The TraceTick backend uses Project Lombok to remove the `getX()` boilerplate from
JPA entities, but only in a constrained, behavior-preserving way. This document
is the canonical policy statement. **The decision itself is recorded in
`docs/architecture/adrs/0006-lombok-entity-policy.md`**; read that first if you
want the *why*.

If you are an agent writing a new entity, read this top-to-bottom **before**
opening your first PR. The policy is enforced at code review against the
acceptance criteria in the tracking ticket (see GitHub issue tracking this
ticket series); there is no automated gate at this layer.

## Why this exists

The codebase deliberately models entities as **rich aggregates**, not anemic
data holders. `User.deactivate()`, `User.activate()`, `User.changeRole(...)`
are hand-written behavior methods; `Customer` is immutable after `create()`.
Lombok is welcome to remove the `getId()` / `getEmail()` boilerplate, but it
is **not** welcome to add back a setter surface that the existing factory
pattern was designed to prevent.

Concretely: writing `@Data` on `User` would silently re-open the bug class
the `private` constructor + `create(...)` factory closed. Writing `@Builder`
would let callers set `id`, `active`, or `createdAt` to whatever they like.
Writing `@NoArgsConstructor` without `access = AccessLevel.PROTECTED` would
let application code `new User()` and produce a row with `created_at = NULL`.

## Allowed annotations on `@Entity` classes

| Annotation | Form | Why this and not a default |
|---|---|---|
| `@Getter` | Class-level | Generates the `getId()`, `getEmail()`, `isActive()` etc. boilerplate. Nothing else. |
| `@EqualsAndHashCode` | **Only with `of = {"id"}`** | Entity equality is identity by id; default would recurse through `@ManyToOne` associations and explode. |
| `@ToString` | **Only with `of = {...}`** listing scalar fields | Default would trigger DB loads on `@ManyToOne(fetch = LAZY)` and explode on collection fields. |
| `@NoArgsConstructor` | **Only with `access = AccessLevel.PROTECTED`** | Hibernate reflects on a no-arg ctor; protected keeps application code from bypassing the factory. |
| `@AllArgsConstructor` | **Only with `access = AccessLevel.PRIVATE`** | Replaces a hand-written private all-args ctor with the same bytecode; protected wouldn't be reachable from inside the class. |
| `@Builder` | **Banned** | Bypasses the `create(...)` factory invariants. Allowed on DTOs. |

## Banned annotations on `@Entity` classes

| Annotation | Why |
|---|---|
| `@Data` | Bundles `@Setter` (violates the aggregate style) + unsafe `@EqualsAndHashCode` + unsafe `@ToString`. The convenience is not worth the bug surface. |
| `@Setter` (class or field) | Reintroduces the setter surface the existing factory pattern prevents. |
| `@Value` | Marks the class `final` (JPA needs subclassing for proxies) and generates a public all-args ctor. |
| `@Builder` | Bypasses factory invariants — see "Allowed" table above. |
| `@ToString` / `@EqualsAndHashCode` without an explicit `of=` list | Both have well-known JPA failure modes (lazy loads, association cycles). |

## Construction pattern

Every entity must follow this exact shape:

```java
@Entity
@Table(name = "...")
@Getter
public class X {

    // ... fields ...

    protected X() {                          // JPA — protected, NOT public
    }

    private X(/* every field */) {           // internal — private
        // ...
    }

    public static X create(/* invariant args */) {
        return new X(/* + defaults */);      // the ONE public construction path
    }

    // behavior methods — never setters
}
```

This matches the existing `User.java` and `Customer.java` after the Lombok
retrofit. Do not deviate from it without updating ADR-0006.

## Allowed on DTOs

DTOs (`api/dto/*`) have no invariants to protect. `@Builder`, `@Data`,
`@Value`, `@Setter` are all fine there. The policy above applies only to
classes annotated with `@Entity`.

## How the policy is enforced today

1. **Reference implementations in the repo.** `domain/User.java` and
   `domain/Customer.java` carry the policy. Reviewers compare new entities
   against them.
2. **This document.** Agents read it before writing their first entity; the
   tracking ticket (issue #T13) links here from its acceptance criteria.
3. **PR review.** A PR that introduces a violating entity is rejected at
   review with a pointer back to this document.
4. **ADR-0006.** Records the decision and its alternatives. If the policy
   changes, this doc and the ADR change together.

## If you want to enforce this in CI

This stack deliberately avoids an in-build gate (ArchUnit, custom lint,
etc.) to keep the dependency surface minimal. If a future ticket needs
hard enforcement, the natural place is an ArchUnit ruleset under
`backend/src/test/java/...` — but that is not part of *this* ticket. Open
a follow-up.

## Cross-references

- `docs/architecture/adrs/0006-lombok-entity-policy.md` — the decision and its alternatives
- `backend/build.gradle.kts` — Lombok 1.18.34 (`compileOnly` + `annotationProcessor`)
- `domain/User.java`, `domain/Customer.java` — reference implementations of the policy
- `CONTEXT.md` — domain glossary; Lombok policy does **not** belong here (it's an
  implementation rule, not a domain term)
