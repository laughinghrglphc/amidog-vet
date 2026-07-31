package com.amidog.app.reservation;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReservationDtosValidationTests {

    private final Validator validator =
            Validation.buildDefaultValidatorFactory().getValidator();
    private final OffsetDateTime start =
            OffsetDateTime.parse("2026-08-10T10:00:00-04:00");

    @Test
    void requestRequiresOneToTenCompleteItemsAndAtMostFiveHundredNoteCharacters() {
        assertThat(validator.validate(new ReservationDtos.CreateReservationRequest(
                start, null, null))).isNotEmpty();
        assertThat(validator.validate(new ReservationDtos.CreateReservationRequest(
                start, Collections.emptyList(), null))).isNotEmpty();
        assertThat(validator.validate(new ReservationDtos.CreateReservationRequest(
                start,
                java.util.stream.LongStream.rangeClosed(1, 11)
                        .mapToObj(id -> new ReservationDtos.ReservationItemRequest(id, id))
                        .toList(),
                null))).isNotEmpty();
        assertThat(validator.validate(new ReservationDtos.CreateReservationRequest(
                start,
                List.of(new ReservationDtos.ReservationItemRequest(null, 2L)),
                null))).isNotEmpty();
        assertThat(validator.validate(new ReservationDtos.CreateReservationRequest(
                start,
                List.of(new ReservationDtos.ReservationItemRequest(1L, null)),
                null))).isNotEmpty();
        assertThat(validator.validate(new ReservationDtos.CreateReservationRequest(
                start,
                List.of(new ReservationDtos.ReservationItemRequest(1L, 2L)),
                "x".repeat(501)))).isNotEmpty();

        assertThat(validator.validate(new ReservationDtos.CreateReservationRequest(
                start,
                List.of(new ReservationDtos.ReservationItemRequest(1L, 2L)),
                "x".repeat(500)))).isEmpty();
    }
}
