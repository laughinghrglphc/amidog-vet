package com.amidog.app.admin;

import com.amidog.app.scheduling.ClinicTime;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Optional;

@Component
public final class AdminRequestValidator {

    static final int MAXIMUM_PAGE_SIZE = 100;
    static final int MAXIMUM_SEARCH_LENGTH = 120;
    /**
     * Administrator filters deliberately accept only four-digit modern ISO
     * dates. This range is portable through JSON, Java zone conversion, JDBC,
     * and PostgreSQL timestamptz while covering all supported clinic history
     * and future scheduling.
     */
    static final LocalDate MINIMUM_FILTER_DATE =
            LocalDate.of(1900, 1, 1);
    static final LocalDate MAXIMUM_FILTER_DATE =
            LocalDate.of(2100, 12, 31);

    private final ClinicTime clinicTime;

    public AdminRequestValidator(ClinicTime clinicTime) {
        this.clinicTime = clinicTime;
    }

    public PageCriteria page(int page, int size) {
        if (page < 0 || size < 1 || size > MAXIMUM_PAGE_SIZE) {
            throw new InvalidAdminRequestException(
                    AdminBadRequestType.INVALID_ADMIN_PAGINATION);
        }
        return new PageCriteria(page, size);
    }

    public Optional<SearchTerm> search(String value) {
        if (value == null || value.trim().isEmpty()) {
            return Optional.empty();
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (normalized.length() > MAXIMUM_SEARCH_LENGTH) {
            throw new InvalidAdminRequestException(
                    AdminBadRequestType.INVALID_ADMIN_SEARCH);
        }
        return Optional.of(new SearchTerm(
                normalized,
                "%" + escapeLike(normalized) + "%"));
    }

    public Optional<DateRange> dateRange(
            LocalDate from,
            LocalDate to) {
        if (from == null && to == null) {
            return Optional.empty();
        }
        if (outsideSupportedRange(from)
                || outsideSupportedRange(to)
                || (from != null
                && to != null
                && from.isAfter(to))) {
            throw new InvalidAdminRequestException(
                    AdminBadRequestType.INVALID_ADMIN_DATE_RANGE);
        }
        try {
            Instant start = from == null
                    ? null
                    : clinicTime.startOfDay(from).toInstant();
            Instant exclusiveEnd = to == null
                    ? null
                    : clinicTime.startOfDay(
                            to.plusDays(1)).toInstant();
            return Optional.of(new DateRange(
                    start, exclusiveEnd));
        } catch (DateTimeException | ArithmeticException exception) {
            throw new InvalidAdminRequestException(
                    AdminBadRequestType.INVALID_ADMIN_DATE_RANGE);
        }
    }

    public Long optionalId(Long value) {
        if (value != null && value <= 0) {
            throw new InvalidAdminRequestException(
                    AdminBadRequestType.INVALID_ADMIN_FILTER);
        }
        return value;
    }

    private static String escapeLike(String value) {
        return value
                .replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_");
    }

    private static boolean outsideSupportedRange(
            LocalDate value) {
        return value != null
                && (value.isBefore(MINIMUM_FILTER_DATE)
                || value.isAfter(MAXIMUM_FILTER_DATE));
    }

    public record PageCriteria(int page, int size) {
        public long offset() {
            return Math.multiplyExact((long) page, size);
        }
    }

    public record SearchTerm(String value, String likePattern) {
    }

    public record DateRange(Instant startInclusive, Instant endExclusive) {
    }
}
