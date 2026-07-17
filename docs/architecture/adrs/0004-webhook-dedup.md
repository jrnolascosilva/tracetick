# ADR-0004: Per-active-incident webhook dedup

- Status: Accepted
- Date: 2026-07-16
- Deciders: TraceTick maintainers

## Context

A monitoring source can fire the same webhook many times for the same underlying incident:

- Repeated firings while the alert is still active (the source is unsure whether the
  receiver got the first one).
- The same alert re-firing after a transient resolution.

Without dedup, a single incident becomes many tickets; with naive dedup, a resolved
incident stays attached to an old ticket forever.

We must define what counts as "the same alert" and how re-fires behave at each lifecycle
state.

## Decision

TraceTick v1 fingerprints a webhook-derived Ticket as `(ingestion_config_id, alert_identity)`
and treats re-fires per the lifecycle:

- `alert_identity` is the source-supplied stable identifier for the incident (commonly
  `payload.alert_id` or `payload.fingerprint`). It is opaque to TraceTick and stored as
  given. If absent, the webhook creates a brand-new Ticket every fire (no dedup).
- On `POST /api/v1/ingest/:url_token`:
  1. Verify HMAC. Reject on failure.
  2. Compute `fingerprint = ingestion_config_id + alert_identity` (when `alert_identity` is
     present).
  3. Look up an existing Ticket with that fingerprint whose state is one of
     `OPEN`, `IN_PROGRESS`, or `RESOLVED` (the **active set**).
  4. If found: increment `refire_count`, append a `REFIRE` Event with payload
     `{refire_count, received_at}`, return the existing Ticket.
  5. If not found: create a new Ticket with the fingerprint and proceed normally.

`CLOSED` is excluded from the active set. A re-fire after CLOSE opens a new Ticket — the old
one stays archived.

## Consequences

Positive:

- Active incidents collapse cleanly into one Ticket with a counter and a chronological
  REFIRE trail.
- After close, a fresh fire gets a fresh Ticket; the old archive is preserved.
- The decision is a single SQL query, indexed on `(fingerprint, state)`.

Negative / risks:

- Two distinct sources cannot share a single Ticket even if their `alert_identity` collides,
  because the fingerprint is keyed on `ingestion_config_id`. That is intentional.
- If a source changes its `alert_identity` format, dedup breaks silently. Document the
  expectation in the IngestionConfiguration admin UI.
- A re-fire while RESOLVED still increments the counter; the Ticket is reopened by the next
  explicit transition, not by the re-fire itself.

## Notes

- The decision lives in the `ingest` module. The Ticket domain does not know about
  fingerprints; it only knows that a Ticket may carry a `fingerprint` and a `refire_count`.
- Re-fires that happen after the system CLOSES the Ticket (auto or manual) create a new
  Ticket — by design.