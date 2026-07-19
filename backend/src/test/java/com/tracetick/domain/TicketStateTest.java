package com.tracetick.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static com.tracetick.domain.TicketState.CLOSED;
import static com.tracetick.domain.TicketState.IN_PROGRESS;
import static com.tracetick.domain.TicketState.OPEN;
import static com.tracetick.domain.TicketState.RESOLVED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class TicketStateTest {

    @ParameterizedTest
    @MethodSource("allowedTransitions")
    void canTransitionToReturnsTrueForAllowedTransitions(TicketState from, TicketState to) {
        assertThat(from.canTransitionTo(to)).isTrue();
    }

    @ParameterizedTest
    @MethodSource("disallowedTransitions")
    void canTransitionToReturnsFalseForDisallowedTransitions(TicketState from, TicketState to) {
        assertThat(from.canTransitionTo(to)).isFalse();
    }

    @Test
    void closedIsTerminalSoItRejectsEveryTransition() {
        for (TicketState to : TicketState.values()) {
            assertThat(CLOSED.canTransitionTo(to))
                    .as("CLOSED must be terminal; transition to %s must be rejected", to)
                    .isFalse();
        }
    }

    @Test
    void openCannotResolveOrCloseDirectly() {
        assertThat(OPEN.canTransitionTo(RESOLVED)).isFalse();
        assertThat(OPEN.canTransitionTo(CLOSED)).isFalse();
    }

    @Test
    void inProgressCannotReopenOrMoveBackwards() {
        assertThat(IN_PROGRESS.canTransitionTo(OPEN)).isFalse();
        assertThat(IN_PROGRESS.canTransitionTo(IN_PROGRESS)).isFalse();
    }

    @Test
    void resolvedCanReopenToInProgressAndCanClose() {
        assertThat(RESOLVED.canTransitionTo(IN_PROGRESS)).isTrue();
        assertThat(RESOLVED.canTransitionTo(CLOSED)).isTrue();
        assertThat(RESOLVED.canTransitionTo(OPEN)).isFalse();
    }

    @Test
    void openCanOnlyMoveToInProgress() {
        assertThat(OPEN.canTransitionTo(IN_PROGRESS)).isTrue();
        assertThat(OPEN.canTransitionTo(OPEN)).isFalse();
        assertThat(OPEN.canTransitionTo(RESOLVED)).isFalse();
        assertThat(OPEN.canTransitionTo(CLOSED)).isFalse();
    }

    @Test
    void requireTransitionToThrowsForDisallowedTransitions() {
        assertThatExceptionOfType(InvalidTicketStateException.class)
                .isThrownBy(() -> CLOSED.requireTransitionTo(OPEN))
                .withMessageContaining("CLOSED")
                .withMessageContaining("OPEN");
    }

    @Test
    void requireTransitionToReturnsTheTargetForAllowedTransitions() {
        assertThat(OPEN.requireTransitionTo(IN_PROGRESS)).isEqualTo(IN_PROGRESS);
    }

    private static Stream<Arguments> allowedTransitions() {
        return Stream.of(
                Arguments.of(OPEN, IN_PROGRESS),
                Arguments.of(IN_PROGRESS, RESOLVED),
                Arguments.of(IN_PROGRESS, CLOSED),
                Arguments.of(RESOLVED, CLOSED),
                Arguments.of(RESOLVED, IN_PROGRESS));
    }

    private static Stream<Arguments> disallowedTransitions() {
        return Stream.of(
                Arguments.of(OPEN, OPEN),
                Arguments.of(OPEN, RESOLVED),
                Arguments.of(OPEN, CLOSED),
                Arguments.of(IN_PROGRESS, OPEN),
                Arguments.of(IN_PROGRESS, IN_PROGRESS),
                Arguments.of(RESOLVED, OPEN),
                Arguments.of(RESOLVED, RESOLVED),
                Arguments.of(CLOSED, OPEN),
                Arguments.of(CLOSED, IN_PROGRESS),
                Arguments.of(CLOSED, RESOLVED),
                Arguments.of(CLOSED, CLOSED));
    }
}
