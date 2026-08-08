package com.amidog.app.reservation;

import com.amidog.app.auth.UserAccount;
import com.amidog.app.client.Client;
import com.amidog.app.common.api.ConflictException;
import com.amidog.app.common.api.ConflictType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReservationLifecycleDomainTests {

    private static final Instant CREATED =
            Instant.parse("2026-07-29T12:00:00Z");
    private static final Instant START =
            Instant.parse("2026-08-10T14:00:00Z");
    private static final Instant MUTATED =
            Instant.parse("2026-07-30T12:00:00Z");
    private static final Set<Transition> APPROVED_TRANSITIONS =
            Set.of(
                    new Transition(
                            ReservationStatus.PENDING,
                            ReservationStatus.CONFIRMED),
                    new Transition(
                            ReservationStatus.PENDING,
                            ReservationStatus.CANCELLED),
                    new Transition(
                            ReservationStatus.CONFIRMED,
                            ReservationStatus.CANCELLED),
                    new Transition(
                            ReservationStatus.CONFIRMED,
                            ReservationStatus.COMPLETED),
                    new Transition(
                            ReservationStatus.CONFIRMED,
                            ReservationStatus.NO_SHOW));

    @ParameterizedTest
    @MethodSource("transitionMatrix")
    void explicitStatusMatrixAllowsOnlyReviewedTransitions(
            ReservationStatus from,
            ReservationStatus to,
            boolean allowed) {
        Reservation reservation = reservation(from);

        if (allowed) {
            reservation.transitionTo(
                    to, ReservationActor.ADMIN, "  decisión clínica  ",
                    MUTATED);

            assertThat(reservation.getStatus()).isEqualTo(to);
            assertThat(reservation.getUpdatedAt()).isEqualTo(MUTATED);
            if (to == ReservationStatus.CANCELLED) {
                assertThat(reservation.getCancelledAt())
                        .isEqualTo(MUTATED);
                assertThat(reservation.getCancelledBy())
                        .isEqualTo("ADMIN");
                assertThat(reservation.getCancellationReason())
                        .isEqualTo("decisi\u00f3n cl\u00ednica");
            } else {
                assertThat(reservation.getCancelledAt()).isNull();
                assertThat(reservation.getCancelledBy()).isNull();
                assertThat(reservation.getCancellationReason()).isNull();
            }
            assertThat(reservation.getEvents()).singleElement()
                    .satisfies(event -> {
                        assertThat(event.getEventType())
                                .isEqualTo(
                                        to == ReservationStatus.CANCELLED
                                                ? ReservationEventType.CANCELLED.name()
                                                : ReservationEventType.STATUS_CHANGED.name());
                        assertThat(event.getActorType())
                                .isEqualTo(ReservationActor.ADMIN.name());
                        assertThat(event.getPreviousStatus()).isEqualTo(from);
                        assertThat(event.getNewStatus()).isEqualTo(to);
                        assertThat(event.getReason())
                                .isEqualTo("decisión clínica");
                        assertThat(event.getCreatedAt()).isEqualTo(MUTATED);
                    });
        } else {
            assertThatThrownBy(() -> reservation.transitionTo(
                    to, ReservationActor.ADMIN, null, MUTATED))
                    .isInstanceOf(ConflictException.class)
                    .satisfies(failure -> assertThat(
                            ((ConflictException) failure).getType())
                            .isEqualTo(
                                    ConflictType
                                            .INVALID_RESERVATION_STATUS_TRANSITION));

            assertThat(reservation.getStatus()).isEqualTo(from);
            assertThat(reservation.getUpdatedAt()).isEqualTo(CREATED);
            assertThat(reservation.getCancelledAt()).isNull();
            assertThat(reservation.getCancelledBy()).isNull();
            assertThat(reservation.getCancellationReason()).isNull();
            assertThat(reservation.getEvents()).isEmpty();
        }
    }

    @Test
    void cancellationNormalizesReasonAndRetainsTheAggregate() {
        Reservation reservation = reservation(ReservationStatus.CONFIRMED);

        reservation.transitionTo(
                ReservationStatus.CANCELLED,
                ReservationActor.CLIENT,
                "  Cambio de planes  ",
                MUTATED);

        assertThat(reservation.getId()).isNull();
        assertThat(reservation.getStatus())
                .isEqualTo(ReservationStatus.CANCELLED);
        assertThat(reservation.getScheduledStart()).isEqualTo(START);
        assertThat(reservation.getScheduledEnd())
                .isEqualTo(START.plus(Duration.ofMinutes(30)));
        assertThat(reservation.getCancelledAt()).isEqualTo(MUTATED);
        assertThat(reservation.getCancelledBy())
                .isEqualTo(ReservationActor.CLIENT.name());
        assertThat(reservation.getCancellationReason())
                .isEqualTo("Cambio de planes");
    }

    @Test
    void blankCancellationReasonBecomesNullAndThreeHundredCharactersAreAllowed() {
        Reservation blank = reservation(ReservationStatus.PENDING);
        blank.transitionTo(
                ReservationStatus.CANCELLED,
                ReservationActor.CLIENT,
                "   ",
                MUTATED);
        assertThat(blank.getCancellationReason()).isNull();
        assertThat(blank.getEvents().getFirst().getReason()).isNull();

        String maximum = "x".repeat(300);
        Reservation bounded = reservation(ReservationStatus.PENDING);
        bounded.transitionTo(
                ReservationStatus.CANCELLED,
                ReservationActor.ADMIN,
                maximum,
                MUTATED);
        assertThat(bounded.getCancellationReason()).hasSize(300);
        assertThat(bounded.getCancelledBy()).isEqualTo("ADMIN");
        assertThat(bounded.getCancelledAt()).isEqualTo(MUTATED);
    }

    @Test
    void overlongReasonIsRejectedBeforeMutationOrEvent() {
        Reservation reservation = reservation(ReservationStatus.PENDING);

        assertThatThrownBy(() -> reservation.transitionTo(
                ReservationStatus.CANCELLED,
                ReservationActor.CLIENT,
                "x".repeat(301),
                MUTATED))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(reservation.getStatus())
                .isEqualTo(ReservationStatus.PENDING);
        assertThat(reservation.getCancelledAt()).isNull();
        assertThat(reservation.getEvents()).isEmpty();
    }

    @Test
    void rescheduleKeepsIdentityAndHistoryAndAppendsOneAuditEvent() {
        Reservation reservation = reservation(ReservationStatus.CONFIRMED);
        Instant moved = START.plus(Duration.ofDays(1));

        reservation.reschedule(
                moved,
                ReservationStatus.CONFIRMED,
                ReservationActor.ADMIN,
                MUTATED);

        assertThat(reservation.getScheduledStart()).isEqualTo(moved);
        assertThat(reservation.getScheduledEnd())
                .isEqualTo(moved.plus(Duration.ofMinutes(30)));
        assertThat(reservation.getStatus())
                .isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(reservation.getClientNote()).isEqualTo("Control");
        assertThat(reservation.getUpdatedAt()).isEqualTo(MUTATED);
        assertThat(reservation.getEvents()).singleElement()
                .satisfies(event -> {
                    assertThat(event.getEventType())
                            .isEqualTo(ReservationEventType.RESCHEDULED.name());
                    assertThat(event.getActorType())
                            .isEqualTo(ReservationActor.ADMIN.name());
                    assertThat(event.getPreviousStatus())
                            .isEqualTo(ReservationStatus.CONFIRMED);
                    assertThat(event.getNewStatus())
                            .isEqualTo(ReservationStatus.CONFIRMED);
                    assertThat(event.getPreviousStart()).isEqualTo(START);
                    assertThat(event.getNewStart()).isEqualTo(moved);
                    assertThat(event.getCreatedAt()).isEqualTo(MUTATED);
                });
    }

    @Test
    void sameStartAndTerminalReschedulesAreRejectedWithoutMutation() {
        Reservation active = reservation(ReservationStatus.PENDING);
        assertThatThrownBy(() -> active.reschedule(
                START,
                ReservationStatus.PENDING,
                ReservationActor.CLIENT,
                MUTATED))
                .isInstanceOf(ConflictException.class)
                .satisfies(failure -> assertThat(
                        ((ConflictException) failure).getType())
                        .isEqualTo(ConflictType.RESERVATION_START_UNCHANGED));
        assertThat(active.getEvents()).isEmpty();

        Reservation terminal = reservation(ReservationStatus.CANCELLED);
        assertThatThrownBy(() -> terminal.reschedule(
                START.plusSeconds(1800),
                ReservationStatus.PENDING,
                ReservationActor.ADMIN,
                MUTATED))
                .isInstanceOf(ConflictException.class)
                .satisfies(failure -> assertThat(
                        ((ConflictException) failure).getType())
                        .isEqualTo(
                                ConflictType
                                    .INVALID_RESERVATION_STATUS_TRANSITION));
        assertThat(terminal.getEvents()).isEmpty();
    }

    private static Reservation reservation(ReservationStatus status) {
        UserAccount account = UserAccount.client(
                "ana@example.com", "{noop}password", CREATED);
        Client client = Client.create(
                account, "Ana", "+56911111111", CREATED);
        return Reservation.create(
                client, START, status, "Control", CREATED);
    }

    private static Stream<Arguments> transitionMatrix() {
        return Stream.of(ReservationStatus.values())
                .flatMap(from -> Stream.of(ReservationStatus.values())
                        .map(to -> Arguments.of(
                                from,
                                to,
                                APPROVED_TRANSITIONS.contains(
                                        new Transition(from, to)))));
    }

    private record Transition(
            ReservationStatus from,
            ReservationStatus to) {
    }
}
