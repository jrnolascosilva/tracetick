/**
 * Authentication and session management: Spring Security configuration, login / logout, and the
 * password-reset flow.
 *
 * <p>Depends on {@code domain} and {@code persistence}. V1 uses session-based auth via httpOnly
 * cookies — no JWTs.
 */
package com.tracetick.auth;