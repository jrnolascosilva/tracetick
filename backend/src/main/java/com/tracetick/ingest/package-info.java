/**
 * Webhook ingestion: receives signed payloads, verifies HMAC, extracts severity and tags,
 * applies fingerprint dedup, and creates tickets.
 *
 * <p>Depends on {@code domain}, {@code persistence}, and {@code api} (for DTOs and error shapes).
 * Exposed as {@code POST /api/v1/ingest/:url_token}.
 */
package com.tracetick.ingest;