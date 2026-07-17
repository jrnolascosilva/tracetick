# ADR-0005: HMAC-SHA256 webhook signature scheme

- Status: Accepted
- Date: 2026-07-16
- Deciders: TraceTick maintainers

## Context

The webhook endpoint `POST /api/v1/ingest/:url_token` is the public ingress for monitoring
alerts. It is unauthenticated by session (webhooks carry no user context) but must reject
spoofed payloads from anyone who learns the URL.

Alternatives considered:

- **Bearer tokens in a header**. Rejected: tokens leak via logs and shoulder-surfing; rotation
  requires coordinating with every monitoring source.
- **IP allowlist**. Rejected: monitoring sources run on dynamic cloud egress IPs; pinning
  CIDRs is brittle and adds operational toil.
- **mTLS**. Rejected: requires provisioning per-source client certificates; operational
  overhead is high for a small self-hosted tool.
- **HMAC-SHA256 of the raw body, secret per IngestionConfiguration**. Chosen.

## Decision

TraceTick v1 authenticates webhooks with HMAC-SHA256:

- Each `IngestionConfiguration` row stores a unique `hmac_secret`.
- The webhook sender computes `signature = hex(hmac_sha256(hmac_secret, raw_body))` and sends
  it in the header `X-TraceTick-Signature: sha256=<hex>`.
- The receiver recomputes the signature over the **raw request body bytes** (before JSON
  parsing) and compares in constant time.
- On mismatch or missing header, the receiver returns `401 Unauthorized` and writes no
  Ticket.

The receiver always reads the body as raw bytes first; signature verification happens
before JSON parsing. JSON parsing happens only after a valid signature is established.

## Consequences

Positive:

- Per-source secrets are revocable by rotating the `hmac_secret` of an
  IngestionConfiguration.
- The signature is deterministic over the body; any tampering invalidates it.
- No IP, certificate, or session state is needed.
- Senders can re-sign with the rotated secret after a published grace period.

Negative / risks:

- Body must be read raw. Any framework middleware that re-encodes or reformats the body
  before our handler sees it will break verification. The ingest controller is responsible
  for reading the raw body explicitly.
- Secrets must be stored encrypted at rest in a future hardening pass; in v1 they are
  stored as-is in the `ingestion_configurations` row.
- Replay attacks are not addressed by HMAC alone. A future spec may add a timestamp + nonce
  check; not in v1.

## Notes

- The decision applies only to webhook ingress. User authentication (sessions, password
  reset) is a separate concern handled by the `auth` module; see T2.
- The header name (`X-TraceTick-Signature`) and the `sha256=` prefix are part of the
  contract. Changing either is a breaking change for senders and must be done via a new
  ADR that supersedes this one.