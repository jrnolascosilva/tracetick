# ADR-0002: Single-tenant posture

- Status: Accepted
- Date: 2026-07-16
- Deciders: TraceTick maintainers

## Context

The spec describes a self-hosted application used by one Customer per deployment. The
`customers` table is intentionally thin — `org_name` and `contact_email` — and there is no
`tenant_id` column on `users`, `tickets`, or any other table. The spec also lists
multi-tenancy as an explicit non-goal for v1.

We must decide whether v1 designs the data model and API surface as if multi-tenancy were
forthcoming, or whether we lean into single-tenant and treat multi-tenancy as a separate
spec.

## Decision

TraceTick v1 is single-tenant: one Customer per running instance. Concretely:

- The `customers` table has exactly one row. It is loaded at startup (and seeded if empty) but
  no code path writes to it from the application; it exists so that the schema is the same
  shape as a future multi-tenant deployment.
- Every row in `users`, `tickets`, `events`, and related tables is implicitly scoped to that
  single Customer. There is no `customer_id` filter on queries; the value is constant.
- No code path distinguishes "this customer's data" from "that customer's data". There are no
  Customer switching, impersonation, or per-Customer admin features.
- API endpoints do not accept a `customer_id` parameter. Roles (`CUSTOMER`, `TECHNICIAN`)
  are sufficient for permission scoping.
- Multi-tenancy is reserved for a future spec. When that spec lands it will introduce
  `customer_id` columns and tenant-scoped query filters; this ADR will be superseded.

## Consequences

Positive:

- Simpler query layer: no `WHERE customer_id = ?` everywhere, no risk of cross-tenant leaks.
- Single-tenant deployment is straightforward (one DB, one app instance).
- The `customers` table acts as a placeholder so the future migration is an additive change
  rather than a refactor.

Negative / risks:

- Code that "knows" there is only one Customer must not be written in a way that makes the
  future multi-tenant migration painful. In particular:
  - Don't hardcode `customers.id = 1`. Look it up at startup.
  - Don't omit foreign keys to `customers.id` where the future schema will require them.
- If a future v1.x release adds a second Customer via direct SQL, the application will behave
  inconsistently. Document this clearly in the README.

## Notes

- Per-user role-scoping (CUSTOMER vs TECHNICIAN) is unrelated to tenancy. CUSTOMER-role
  users in v1 see only Tickets they reported or watch; that filter has nothing to do with
  multi-tenancy and survives the future migration unchanged.