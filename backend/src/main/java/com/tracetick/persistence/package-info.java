/**
 * Persistence layer: Spring Data JPA repositories and Liquibase-managed schema.
 *
 * <p>Depends on {@code domain}. Entity tables are introduced by Liquibase changesets under
 * {@code src/main/resources/db/changelog}.
 */
package com.tracetick.persistence;