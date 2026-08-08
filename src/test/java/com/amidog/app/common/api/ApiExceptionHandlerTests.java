package com.amidog.app.common.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.sql.SQLException;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApiExceptionHandlerTests {

    private final MockMvc mvc = MockMvcBuilders
            .standaloneSetup(new ErrorController())
            .setControllerAdvice(new ApiExceptionHandler())
            .build();

    @Test
    void validationUsesTheUnifiedThreeFieldContract() throws Exception {
        mvc.perform(post("/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("La solicitud contiene datos inválidos."))
                .andExpect(jsonPath("$.errors.value").exists());
    }

    @Test
    void malformedJsonAndTypeMismatchUseClosed400Envelopes() throws Exception {
        mvc.perform(post("/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message")
                        .value("La solicitud contiene datos inválidos."))
                .andExpect(jsonPath("$.errors").isEmpty());

        mvc.perform(get("/number").param("value", "not-a-number"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message")
                        .value("La solicitud contiene datos inválidos."))
                .andExpect(jsonPath("$.errors").isEmpty());
    }

    @Test
    void domainAndRateLimitErrorsUseStableCodesWithoutInternalDetails() throws Exception {
        mvc.perform(post("/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.errors").isEmpty());
        mvc.perform(post("/conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
        mvc.perform(post("/limited"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"))
                .andExpect(jsonPath("$.message").value("Demasiadas solicitudes. Intenta nuevamente más tarde."));
    }

    @Test
    void everyStructuredConflictUsesOnlyItsClosedDescriptor() throws Exception {
        for (ConflictType type : ConflictType.values()) {
            mvc.perform(post("/structured-conflict/{type}", type.name()))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value(type.code()))
                    .andExpect(jsonPath("$.message").value(type.message()))
                    .andExpect(jsonPath("$.errors").isEmpty());
        }
    }

    @Test
    void occupiedSlotCollisionUsesTheExactReviewed409Envelope() throws Exception {
        mvc.perform(post("/structured-conflict/SLOT_ALREADY_BOOKED"))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code")
                        .value("SLOT_ALREADY_BOOKED"))
                .andExpect(jsonPath("$.message").value(
                        "Ese horario acaba de ser reservado. Elige otro bloque disponible."))
                .andExpect(jsonPath("$.errors").isEmpty())
                .andExpect(jsonPath("$.details").doesNotExist());
    }

    @Test
    void reservationConflictDetailsAreTypedSortedAndDoNotReopenArbitraryPayloads()
            throws Exception {
        mvc.perform(post("/block-conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BLOCK_OVERLAPS_RESERVATIONS"))
                .andExpect(jsonPath("$.errors").isEmpty())
                .andExpect(jsonPath("$.details.reservationIds[0]").value(4))
                .andExpect(jsonPath("$.details.reservationIds[1]").value(12));

        assertThatThrownBy(() ->
                ConflictException.blockOverlapsReservations(java.util.List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nullStructuredDescriptorIsRejectedBeforeItCanReachTheHandler() {
        assertThatThrownBy(() -> new ConflictException((ConflictType) null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("type");
        assertThatThrownBy(() -> new ConflictException((String) null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("clientSafeMessage");
        assertThatThrownBy(() -> new ConflictException("  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("clientSafeMessage must not be blank");
    }

    @Test
    void unexpectedFailuresUseOneGeneric500EnvelopeWithoutInternalDetails()
            throws Exception {
        mvc.perform(post("/unexpected"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value(
                        "Ocurrió un error inesperado. Intenta nuevamente más tarde."))
                .andExpect(jsonPath("$.errors").isEmpty())
                .andExpect(header().string(
                        "X-Correlation-ID",
                        matchesPattern(
                                "[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}"
                                        + "-[89ab][0-9a-f]{3}-[0-9a-f]{12}")))
                .andExpect(content().string(not(containsString(
                        "password=super-secret"))))
                .andExpect(content().string(not(containsString(
                        "reservations_no_occupied_overlap"))))
                .andExpect(content().string(not(containsString(
                        SQLException.class.getName()))))
                .andExpect(content().string(not(containsString(
                        "select * from users"))))
                .andExpect(content().string(not(containsString(
                        "C:\\private\\credentials.txt"))))
                .andExpect(content().string(not(containsString(
                        "token=raw-token"))));
    }

    @RestController
    static class ErrorController {
        @PostMapping("/validation")
        void validation(@Valid @RequestBody Input input) {
        }

        @GetMapping("/number")
        void number(@RequestParam int value) {
        }

        @PostMapping("/not-found")
        void notFound() {
            throw new NotFoundException("El recurso solicitado no existe.");
        }

        @PostMapping("/conflict")
        void conflict() {
            throw new ConflictException("La operación entra en conflicto con el estado actual.");
        }

        @PostMapping("/limited")
        void limited() {
            throw new TooManyRequestsException();
        }

        @PostMapping("/structured-conflict/{type}")
        void structuredConflict(@PathVariable ConflictType type) {
            throw new ConflictException(type);
        }

        @PostMapping("/block-conflict")
        void blockConflict() {
            throw ConflictException.blockOverlapsReservations(
                    java.util.List.of(12L, 4L, 12L));
        }

        @PostMapping("/unexpected")
        void unexpected() {
            throw new IllegalStateException(
                    "password=super-secret; constraint="
                            + "reservations_no_occupied_overlap; path="
                            + "C:\\private\\credentials.txt; token=raw-token",
                    new SQLException("select * from users"));
        }
    }

    record Input(@NotBlank String value) {
    }
}
