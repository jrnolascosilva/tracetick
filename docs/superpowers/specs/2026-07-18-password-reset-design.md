# Password Reset Design

**Issue:** #4 — T3: Password reset  
**Date:** 2026-07-18

## Goal

Provide an end-to-end password-reset flow for an unauthenticated User. A User requests a one-time token, confirms it with a new password, and then signs in through the existing login flow.

The v1 notifications module remains a stub, so the request endpoint returns the raw token directly. This response behavior is suitable only for the v1 development flow and must be replaced by out-of-band delivery before production use. Removing `token` from the response is a future API-contract change that must be tracked separately and versioned or coordinated with the frontend.

## Scope

- Public request and confirmation API endpoints.
- Durable, single-use, time-limited reset tokens.
- BCrypt password replacement through User domain behavior.
- Public `/password-reset` request and confirmation UI.
- Backend HTTP acceptance tests and focused domain/service tests.
- Frontend component tests using Vitest, React Testing Library, and MSW, including the repository's initial frontend test-runner setup.

Email delivery, rate limiting, automatic login, and changes to the existing login email-matching behavior are out of scope.

## Architecture

A transactional `PasswordResetService` owns token issuance and confirmation. `AuthController` exposes the service through two public endpoints. `PasswordResetToken` models token lifecycle, while `PasswordResetTokenRepository` persists token records. `User` gains one behavior method that replaces its password hash without exposing a public setter.

The service depends on:

- `UserRepository` for User lookup and password updates.
- `PasswordResetTokenRepository` for token lifecycle persistence.
- `PasswordEncoder` for BCrypt hashing.
- `Clock` for deterministic expiry checks.
- `SecureRandom` for token generation.

Production uses a UTC system clock. Tests replace it with a fixed or mutable clock.

## Token Model

Add a `password_reset_tokens` table with:

- `id`: primary key.
- `user_id`: foreign key to `users`.
- `token_hash`: unique SHA-256 digest of the raw token.
- `expires_at`: expiration instant.
- `invalidated_at`: nullable instant set when the token is used or superseded.
- `created_at`: issuance instant.

Only the SHA-256 digest is persisted. Real and decoy tokens use the same generator: 32 cryptographically random bytes encoded as unpadded Base64URL per RFC 4648 section 5. They therefore have the same response length and character set.

Tokens expire one hour after issuance by default. The ISO-8601 duration property `tracetick.auth.password-reset.token-ttl` defaults to `PT1H`. A token is valid only when its digest exists, `invalidated_at` is null, and `expires_at` is after the current clock instant.

Issuing a new token invalidates all older unused tokens for that User. A `UserRepository` query annotated with `@Lock(PESSIMISTIC_WRITE)` loads the User during issuance, serializing concurrent requests before existing tokens are invalidated and the replacement is inserted. Confirmation loads the matching token through a repository query annotated with `@Lock(PESSIMISTIC_WRITE)` so concurrent submissions cannot both succeed.

## API Contract

### Request reset

`POST /api/v1/auth/password-reset`

Request:

```json
{
  "email": "alex@example.com"
}
```

Successful response:

```json
{
  "token": "url-safe-random-token"
}
```

The endpoint returns `200 OK` with the same response shape for every syntactically valid email. An active matching User receives a persisted real token. Unknown or inactive emails receive an indistinguishable random decoy token that is not persisted. This avoids revealing account existence in the request response, although returning tokens directly remains a v1-only limitation.

Input validation follows existing auth conventions: email is required, must be a valid email address, and is limited to 255 characters.

### Confirm reset

`POST /api/v1/auth/password-reset/confirm`

Request:

```json
{
  "token": "url-safe-random-token",
  "new_password": "new-password"
}
```

The snake-case `new_password` field is required by issue #4. The backend DTO maps it explicitly with `@JsonProperty("new_password")`; this does not change the repository's default camel-case JSON strategy. The frontend sends the same wire name.

A successful confirmation atomically:

1. Locks and validates the token record.
2. BCrypt-hashes the new password.
3. Replaces the User password hash through domain behavior.
4. Invalidates the token.
5. Returns `204 No Content`.

The endpoint does not create a session. The User signs in through `/login` with the new password.

Validation and failure responses:

- Malformed body, blank token, unknown token, or invalid password: `400 Bad Request`.
- Previously invalidated token, including a reused or superseded token: `409 Conflict`.
- Expired token: `410 Gone`.
- New password length: 8–255 characters, matching User creation.

Spring Security permits only `POST /api/v1/auth/password-reset` and `POST /api/v1/auth/password-reset/confirm` through explicit `requestMatchers(HttpMethod.POST, ...)` entries before `anyRequest().authenticated()`. All other authorization rules remain unchanged.

## Frontend Interaction

Add `/password-reset` as a public route beside `/login`. Add a “Forgot password?” link to the login form so the flow is discoverable. If login has a `next` query parameter, the link carries it through reset and the post-confirmation sign-in action restores it.

The page has two states using the existing login card visual language:

1. **Request:** collect an email and submit the request endpoint.
2. **Confirm:** prefill the returned token, collect and confirm the new password, and submit the confirmation endpoint.

The page also accepts `?token=...` and opens directly in confirmation state, preserving the route contract needed for future email links. Users may replace the prefilled token manually. A successful confirmation shows a clear success state with an action to open `/login`; it does not log the User in automatically.

Client-side validation checks required fields, matching passwords, and the 8–255 character password length before submission. Pending submissions disable their buttons. Errors and success messages use accessible live/status semantics consistent with the existing login form.

The API client's request helper gains a per-call `redirectOnUnauthorized` option that defaults to `true`. The two password-reset methods pass `false`, preventing a reset API failure from invoking the global unauthenticated redirect without coupling generic request behavior to `window.location.pathname`.

## Error Handling and Transactions

The service owns reset-specific outcomes so HTTP mapping remains thin and deterministic. Existing request validation continues to use Jakarta Bean Validation. Reset failures do not expose password hashes, stored token hashes, or User details.

Token validation and password replacement run in one transaction. A failure before commit leaves both the password and token unchanged. A successful transaction makes the password update and token invalidation visible together.

## Testing Strategy

### Backend

Drive the acceptance criteria first through `@SpringBootTest`, MockMvc, and Testcontainers at the HTTP/JSON seam:

- A reset request for an active User returns a non-empty token.
- Confirming a valid token returns 204, changes the BCrypt password hash, and permits login with the new password.
- Confirming the same token again returns 409.
- Confirming an expired token returns 410 and leaves the password unchanged.

Focused domain/service tests cover:

- Raw token hashing and lookup behavior.
- One-hour configurable expiry using a controlled `Clock`.
- Invalidated and expired token transitions.
- Decoy responses for unknown and inactive emails.
- Superseding older unused tokens.

### Frontend

Bootstrap the repository's frontend test infrastructure in this ticket: add Vitest, React Testing Library, jest-dom matchers, jsdom, and MSW; add the Vite test configuration and `test` script; and install shared test setup for jest-dom and the MSW server. A test-local `renderPasswordReset(initialEntries)` helper uses `createMemoryRouter` and `RouterProvider` so each case controls its starting URL while exercising the real route component. Mock only the HTTP seam. Cover:

- Email request transitions to confirmation and prefills the returned token.
- `?token=` opens confirmation directly.
- Mismatched or invalid passwords do not submit.
- Successful confirmation shows the sign-in action.
- API failures render an accessible error without redirecting away from the public route.

During implementation, run the active backend or frontend test file and the relevant typecheck after each coherent change. At completion, run all backend tests/build checks and all frontend tests, typechecking, linting, and production build.

## Migration and Compatibility

Liquibase adds the token table in `0003-password-reset-tokens.yaml` and includes it from the master changelog. Existing User rows require no data migration. Existing sessions, login/logout behavior, and admin User APIs remain compatible.
