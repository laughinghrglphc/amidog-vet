package com.amidog.app.catalog;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ServiceDtosValidationTests {

    private final Validator validator =
            Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void rawServiceCodeContractRejectsSixtyOneCharactersBeforeNormalization() {
        var request = new ServiceDtos.ServiceCreateRequest(
                "a".repeat(30) + " " + "b".repeat(30),
                "Consulta",
                null,
                0);

        assertThat(validator.validate(request))
                .singleElement()
                .extracting(violation -> violation.getPropertyPath().toString())
                .isEqualTo("code");
    }
}
