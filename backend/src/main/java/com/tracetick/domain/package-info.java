/**
 * Domain layer: entities, value objects, and core business invariants.
 *
 * <p>This package is the dependency root: every other backend module may depend on it, but it
 * depends on nothing else in the project. In v1 it will host the entities defined in
 * {@code CONTEXT.md} ({@code Customer}, {@code User}, {@code Ticket}, {@code Tag},
 * {@code IngestionConfiguration}, {@code Event}) and the {@code Ticket} state machine.
 */
package com.tracetick.domain;