package com.amidog.app.reservation;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class ReservationLifecycleDtosValidationTests {

    private final Validator validator =
            Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void cancellationAndStatusReasonsUseNormalizedLengthAtThreeHundredCharacters() {
        String surroundedMaximum =
                "  " + "x".repeat(300) + "  ";
        String surroundedOverLimit =
                "  " + "x".repeat(301) + "  ";

        assertThat(validator.validate(
                new ReservationLifecycleDtos.CancelReservationRequest(
                        surroundedMaximum))).isEmpty();
        assertThat(validator.validate(
                new ReservationLifecycleDtos.StatusChangeRequest(
                        ReservationStatus.CANCELLED,
                        surroundedMaximum))).isEmpty();
        assertThat(validator.validate(
                new ReservationLifecycleDtos.CancelReservationRequest(
                        surroundedOverLimit))).singleElement()
                .satisfies(violation -> assertThat(
                        violation.getPropertyPath().toString())
                        .isEqualTo("reason"));
        assertThat(validator.validate(
                new ReservationLifecycleDtos.StatusChangeRequest(
                        ReservationStatus.CANCELLED,
                        surroundedOverLimit))).singleElement()
                .satisfies(violation -> assertThat(
                        violation.getPropertyPath().toString())
                        .isEqualTo("reason"));
    }

    @Test
    void statusAndRescheduleStartAreRequired() {
        assertThat(validator.validate(
                new ReservationLifecycleDtos.StatusChangeRequest(
                        null, null))).hasSize(1);
        assertThat(validator.validate(
                new ReservationLifecycleDtos.RescheduleRequest(
                        null))).hasSize(1);
        assertThat(validator.validate(
                new ReservationLifecycleDtos.RescheduleRequest(
                        OffsetDateTime.parse(
                                "2026-08-10T10:00:00-04:00"))))
                .isEmpty();
    }
}
