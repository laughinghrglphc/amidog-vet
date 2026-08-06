package com.amidog.app.reservation;

import org.junit.jupiter.api.Test;
import org.postgresql.util.PSQLException;
import org.postgresql.util.ServerErrorMessage;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;

class ReservationCollisionTranslatorTests {

    @Test
    void onlyTheExactOccupiedReservationExclusionIsRecognized() {
        assertThat(ReservationCreationService.isOccupiedSlotCollision(
                violation("23P01", "reservations_no_occupied_overlap"))).isTrue();
        assertThat(ReservationCreationService.isOccupiedSlotCollision(
                violation("23505", "reservations_no_occupied_overlap"))).isFalse();
        assertThat(ReservationCreationService.isOccupiedSlotCollision(
                violation("23P01", "some_other_exclusion"))).isFalse();
        assertThat(ReservationCreationService.isOccupiedSlotCollision(
                new DataIntegrityViolationException(
                        "23P01 reservations_no_occupied_overlap"))).isFalse();
    }

    private static DataIntegrityViolationException violation(
            String state, String constraint) {
        ServerErrorMessage metadata = new ServerErrorMessage("") {
            @Override
            public String getSQLState() {
                return state;
            }

            @Override
            public String getConstraint() {
                return constraint;
            }
        };
        return new DataIntegrityViolationException(
                "sanitized wrapper", new PSQLException(metadata));
    }
}
