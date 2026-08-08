package com.amidog.app.admin;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class AdminDtosValidationTests {

    private final Validator validator =
            Validation.buildDefaultValidatorFactory()
                    .getValidator();

    @Test
    void clientFullFormRequiresActiveAndFrozenV7Lengths() {
        assertThat(validator.validate(
                new AdminDtos.AdminClientUpdateRequest(
                        "Ana", "+56911111111", null)))
                .extracting(value ->
                        value.getPropertyPath().toString())
                .containsExactly("active");
        assertThat(validator.validate(
                new AdminDtos.AdminClientUpdateRequest(
                        "x".repeat(121),
                        "9".repeat(31),
                        true)))
                .extracting(value ->
                        value.getPropertyPath().toString())
                .containsExactlyInAnyOrder("name", "phone");
    }

    @Test
    void petFullFormAllowsClearingOptionalsAndRejectsFrozenBoundsAndFutureDates() {
        assertThat(validator.validate(
                new AdminDtos.AdminPetUpdateRequest(
                        "Luna", "Perro", null, null)))
                .isEmpty();
        assertThat(validator.validate(
                new AdminDtos.AdminPetUpdateRequest(
                        "x".repeat(81),
                        "x".repeat(41),
                        "x".repeat(81),
                        LocalDate.of(2099, 1, 1))))
                .extracting(value ->
                        value.getPropertyPath().toString())
                .containsExactlyInAnyOrder(
                        "name", "species", "breed", "birthdate");
    }
}
