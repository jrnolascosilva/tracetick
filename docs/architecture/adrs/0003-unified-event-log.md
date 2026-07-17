# ADR-0003: Unified Event log

- Status: Accepted
- Date: 2026-07-16
- Deciders: TraceTick maintainers

## Context

A Ticket has a timeline of activity: who reported it, who got assigned, who transitioned
state, who commented, what webhook re-fires happened. Several designs are possible:

- **Separate tables per event type** (`comments`, `state_changes`, `assignments`). Easy to
  type each row, painful to render a single timeline.
- **Polymorphic table with a discriminator and a JSONB payload**. One table, easy to render
  and append, harder to query per type and to constrain payload shape.
- **One `events` table with a typed payload column plus a type discriminator**. Compromise:
  render is trivial, per-type reads are simple, and the payload schema is recorded as a
  Liquibase comment per type so it stays reviewable.

The spec requires a Ticket timeline that mixes comments with state changes and re-fires.
The list of Event types is finite and small: `COMMENT`, `STATE_CHANGE`, `ASSIGNMENT_CHANGE`,
`SEVERITY_CHANGE`, `REFIRE`, `ATTACHMENT_ADDED`.

## Decision

TraceTick v1 stores all Ticket activity in a single `events` table:

```sql
events(
  id            BIGSERIAL PRIMARY KEY,
  ticket_id     BIGINT NOT NULL REFERENCES tickets(id),
  type          TEXT NOT NULL,                 -- COMMENT, STATE_CHANGE, ASSIGNMENT_CHANGE,
                                              -- SEVERITY_CHANGE, REFIRE, ATTACHMENT_ADDED
  actor_user_id BIGINT REFERENCES users(id),   -- NULL for system-generated events
  payload       JSONB NOT NULL DEFAULT '{}',   -- type-specific shape; see Liquibase changeset
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
)
```

- Type is a string enum (constrained at the application layer in v1; later tickets may add a
  CHECK constraint).
- Payload is JSONB. Per-type shape is documented in the Liquibase changeset that introduces
  the type. Renderers switch on `type` and parse `payload` accordingly.
- Events are append-only. There is no UPDATE or DELETE on this table.
- The Ticket timeline is a single SELECT ordered by `created_at, id`.
- Each state-mutating action on a Ticket writes one Event in the same transaction as the
  mutation. There is no separate "audit log" table; the Event log **is** the audit log.

## Consequences

Positive:

- One table, one SELECT to render a timeline.
- New Event types are additive: add a row in code, document the payload shape, ship.
- Append-only makes the table safe for read replicas and partial archival.

Negative / risks:

- The payload shape is type-dependent. Reviewers must read the changeset comment or the
  producer to understand each type. Mitigated by keeping the changeset comment in lock-step
  with the code.
- Cross-type queries (e.g. "last 10 comments system-wide") need a `WHERE type = ?` predicate
  plus a JSONB index if they become hot. None are expected in v1.

## Notes

- The `ATTACHMENT_ADDED` type is reserved. v1 ships no upload endpoint; later specs layer it
  on.
- An Event whose `actor_user_id` is NULL is a system event (e.g. a REFIRE from a webhook).
  The renderer treats those slightly differently (no avatar, label = "System").