# TraceTick

A self-hosted technical support ticketing and infrastructure incident tracking system.

## Language

**Customer**:
An organization being supported by TraceTick. The unit of tenancy and data isolation. In single-tenant mode, one Customer per instance; carries only `org_name` and `contact_email`. Branding, support hours, and other deploy-time config live outside the DB.
_Avoid_: Account, tenant, organization

**User**:
A person who logs in. Belongs to one Customer. Has exactly one role: `CUSTOMER` or `TECHNICIAN`.
Permissions: `TECHNICIAN`s see all Tickets, assign, and transition state; `CUSTOMER`s see and act only on Tickets they reported or watch.
_Avoid_: Member, account, person

**Ticket**:
A unit of work in the support queue. Created via either origin: `HUMAN` (submitted by a Customer-User) or `WEBHOOK` (auto-created from an ingestion). Belongs to a Customer; opened by a User (reporter, may be null for webhook origin).
Assignment: one primary assignee (TECHNICIAN-role User, nullable), zero or more watchers (any role).
Lifecycle states: `OPEN` → `IN_PROGRESS` → `RESOLVED` → `CLOSED`. `RESOLVED` is soft-terminal (reopenable to `IN_PROGRESS`); `CLOSED` is hard-terminal (archived).
Severity: one of `CRITICAL` / `HIGH` / `MEDIUM` / `LOW`. Webhook payloads carry it where present; `HUMAN`-origin defaults to `MEDIUM`.
Tags: key-value (`service:api`, `env:prod`). Inherited from IngestionConfiguration defaults and any labels extracted from webhook payloads.
WEBHOOK-origin tickets dedup by fingerprint (`ingestion_config + alert identity`); a re-fire while active increments a counter on the ticket, after RESOLVED/CLOSED it opens a new one.
_Avoid_: Issue, case, request

**Tag**:
A key-value label on a Ticket (`service:api`, `env:prod`, `team:platform`). Used for filtering, routing, and grouping. Inherited from the IngestionConfiguration and webhook payload; editable on the Ticket.
_Avoid_: Label

**IngestionConfiguration**:
A configuration that turns incoming webhooks into Tickets. Each row is one webhook source: a unique URL token, an HMAC secret for signature verification, and defaults applied to created tickets (severity, assignee, tags).
_Avoid_: webhook rule, alert source, alert route

**Event**:
A single change or interaction recorded on a Ticket's timeline. Types include `COMMENT` (text from a User), `STATE_CHANGE`, `ASSIGNMENT_CHANGE`, `SEVERITY_CHANGE`, `REFIRE`, `ATTACHMENT_ADDED`. Append-only, chronologically ordered.
_Avoid_: Activity, log entry, history item