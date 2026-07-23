package com.amidog.app.admin;

import com.amidog.app.config.AmidogProperties;
import com.amidog.app.scheduling.ClinicTime;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.TimeZone;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AdminRequestValidatorTests {

    private final AdminRequestValidator validator =
            new AdminRequestValidator(clinicTime());

    @Test
    void validatesPaginationAndCapsThePublicContract() {
        assertThat(validator.page(0, 1))
                .isEqualTo(new AdminRequestValidator.PageCriteria(0, 1));
        assertThat(validator.page(3, 100))
                .isEqualTo(new AdminRequestValidator.PageCriteria(3, 100));

        assertThatThrownBy(() -> validator.page(-1, 25))
                .isInstanceOf(InvalidAdminRequestException.class)
                .extracting("type")
                .isEqualTo(AdminBadRequestType.INVALID_ADMIN_PAGINATION);
        assertThatThrownBy(() -> validator.page(0, 101))
                .isInstanceOf(InvalidAdminRequestException.class)
                .extracting("type")
                .isEqualTo(AdminBadRequestType.INVALID_ADMIN_PAGINATION);
    }

    @Test
    void trimsBoundsAndEscapesLiteralLikeWildcards() {
        assertThat(validator.search("  Ana%_! PÉREZ  "))
                .contains(new AdminRequestValidator.SearchTerm(
                        "ana%_! pérez",
                        "%ana!%!_!! pérez%"));
        assertThat(validator.search("   ")).isEmpty();

        assertThatThrownBy(() -> validator.search("x".repeat(121)))
                .isInstanceOf(InvalidAdminRequestException.class)
                .extracting("type")
                .isEqualTo(AdminBadRequestType.INVALID_ADMIN_SEARCH);
    }

    @Test
    void convertsInclusiveClinicDatesUsingRealSantiagoOffsetsNotJvmDefault() {
        TimeZone original = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"));
        try {
            assertThat(validator.dateRange(
                    LocalDate.of(2026, 4, 4),
                    LocalDate.of(2026, 4, 5)))
                    .contains(new AdminRequestValidator.DateRange(
                            Instant.parse("2026-04-04T03:00:00Z"),
                            Instant.parse("2026-04-06T04:00:00Z")));
        } finally {
            TimeZone.setDefault(original);
        }
    }

    @Test
    void rejectsAnInvertedDateRange() {
        assertThatThrownBy(() -> validator.dateRange(
                LocalDate.of(2026, 8, 11),
                LocalDate.of(2026, 8, 10)))
                .isInstanceOf(InvalidAdminRequestException.class)
                .extracting("type")
                .isEqualTo(AdminBadRequestType.INVALID_ADMIN_DATE_RANGE);
    }

    @Test
    void rejectsJavaAndDatabaseExtremeDatesBeforeArithmeticOrZoneConversion() {
        assertThatThrownBy(() -> validator.dateRange(
                LocalDate.MIN, null))
                .isInstanceOf(InvalidAdminRequestException.class)
                .extracting("type")
                .isEqualTo(AdminBadRequestType.INVALID_ADMIN_DATE_RANGE);
        assertThatThrownBy(() -> validator.dateRange(
                null, LocalDate.MAX))
                .isInstanceOf(InvalidAdminRequestException.class)
                .extracting("type")
                .isEqualTo(AdminBadRequestType.INVALID_ADMIN_DATE_RANGE);
        assertThatThrownBy(() -> validator.dateRange(
                LocalDate.of(1899, 12, 31),
                LocalDate.of(1900, 1, 1)))
                .isInstanceOf(InvalidAdminRequestException.class)
                .extracting("type")
                .isEqualTo(AdminBadRequestType.INVALID_ADMIN_DATE_RANGE);
        assertThatThrownBy(() -> validator.dateRange(
                LocalDate.of(2100, 12, 31),
                LocalDate.of(2101, 1, 1)))
                .isInstanceOf(InvalidAdminRequestException.class)
                .extracting("type")
                .isEqualTo(AdminBadRequestType.INVALID_ADMIN_DATE_RANGE);

        assertThat(validator.dateRange(
                LocalDate.of(1900, 1, 1),
                LocalDate.of(2100, 12, 31)))
                .isPresent();
    }

    @Test
    void rejectsNonPositiveRelatedIdentifiersAndKeepsPageMathOverflowSafe() {
        assertThat(validator.optionalId(42L)).isEqualTo(42L);
        assertThatThrownBy(() -> validator.optionalId(0L))
                .isInstanceOf(InvalidAdminRequestException.class)
                .extracting("type")
                .isEqualTo(AdminBadRequestType.INVALID_ADMIN_FILTER);

        assertThat(PageResponse.of(
                List.of(), 0, 100, Long.MAX_VALUE).totalPages())
                .isEqualTo(Integer.MAX_VALUE);
        assertThat(validator.page(
                Integer.MAX_VALUE, 100).offset())
                .isEqualTo(214_748_364_700L);
    }

    private static ClinicTime clinicTime() {
        return new ClinicTime(new AmidogProperties(
                ZoneId.of("America/Santiago"),
                URI.create("http://localhost"),
                URI.create("http://localhost"),
                new AmidogProperties.Booking(false, 30, 2, 90),
                new AmidogProperties.Admin("", "", "Administradora", ""),
                new AmidogProperties.Contact("", ""),
                new AmidogProperties.Email("log")));
    }
}
