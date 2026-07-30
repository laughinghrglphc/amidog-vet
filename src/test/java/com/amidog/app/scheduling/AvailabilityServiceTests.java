package com.amidog.app.scheduling;

import com.amidog.app.config.AmidogProperties;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AvailabilityServiceTests {

    private static final ZoneId SANTIAGO = ZoneId.of("America/Santiago");

    @Test
    void splitAndOverlappingIntervalsProduceUniqueAscendingThirtyMinuteSlots() {
        Fixture fixture = new Fixture(
                Instant.parse("2026-08-10T12:00:00Z"), 0, 90,
                List.of(
                        WeeklyAvailability.create(1, LocalTime.of(9, 0), LocalTime.of(10, 30), true),
                        WeeklyAvailability.create(1, LocalTime.of(10, 0), LocalTime.of(11, 0), true),
                        WeeklyAvailability.create(1, LocalTime.of(15, 0), LocalTime.of(16, 0), true)));

        assertThat(fixture.service.availableSlots(date("2026-08-10"), date("2026-08-10")))
                .extracting(slot -> slot.startsAt().toLocalTime().toString())
                .containsExactly("09:00", "09:30", "10:00", "10:30", "15:00", "15:30");
    }

    @Test
    void blackoutAndOccupiedRangesUseHalfOpenOverlapSoAdjacencyRemainsAvailable() {
        Fixture fixture = new Fixture(
                Instant.parse("2026-08-10T11:00:00Z"), 0, 90,
                List.of(WeeklyAvailability.create(
                        1, LocalTime.of(9, 0), LocalTime.of(11, 30), true)));
        fixture.blocks = List.of(new AvailabilityService.TimeRange(
                instant("2026-08-10T09:30:00-04:00"),
                instant("2026-08-10T10:00:00-04:00")));
        fixture.occupied = List.of(new OccupiedSlotReader.OccupiedSlot(
                7L,
                instant("2026-08-10T10:30:00-04:00"),
                instant("2026-08-10T11:00:00-04:00")));

        assertThat(fixture.rebuild().availableSlots(date("2026-08-10"), date("2026-08-10")))
                .extracting(slot -> slot.startsAt().toLocalTime().toString())
                .containsExactly("09:00", "10:00", "11:00");
        assertThat(fixture.blockQueries).isEqualTo(1);
        assertThat(fixture.occupiedQueries).isEqualTo(1);
    }

    @Test
    void noticeBoundaryIsInclusiveAndPastOrBeyondHorizonRangesAreEmpty() {
        Fixture fixture = new Fixture(
                instant("2026-08-10T08:00:00-04:00"), 2, 2,
                List.of(WeeklyAvailability.create(
                        1, LocalTime.of(9, 30), LocalTime.of(11, 0), true)));

        assertThat(fixture.service.availableSlots(date("2026-08-10"), date("2026-08-10")))
                .extracting(slot -> slot.startsAt().toLocalTime().toString())
                .containsExactly("10:00", "10:30");
        assertThat(fixture.service.availableSlots(date("2026-08-01"), date("2026-08-09"))).isEmpty();
        assertThat(fixture.service.availableSlots(date("2026-08-13"), date("2030-01-01"))).isEmpty();
    }

    @Test
    void hugeRequestedSpanIsClampedBeforeRepositoryQueriesAndReverseRangeIsRejected() {
        Fixture fixture = new Fixture(
                instant("2026-08-10T08:00:00-04:00"), 0, 2,
                List.of(WeeklyAvailability.create(
                        1, LocalTime.of(9, 0), LocalTime.of(10, 0), true)));

        fixture.service.availableSlots(date("1900-01-01"), date("2200-01-01"));

        assertThat(fixture.lastBlockRange)
                .containsExactly(
                        fixture.clinicTime.startOfDay(date("2026-08-10")).toInstant(),
                        fixture.clinicTime.startOfDay(date("2026-08-13")).toInstant());
        assertThatThrownBy(() ->
                fixture.service.availableSlots(date("2026-08-11"), date("2026-08-10")))
                .isInstanceOfSatisfying(InvalidSchedulingRequestException.class,
                        error -> assertThat(error.getType())
                                .isEqualTo(SchedulingBadRequestType.INVALID_DATE_RANGE));
    }

    @Test
    void slotGenerationSkipsActualNonexistentSantiagoCandidatesAndIgnoresJvmDefaultZone() {
        var gap = nextGap();
        LocalDate date = gap.getDateTimeBefore().toLocalDate();
        int day = date.getDayOfWeek().getValue();
        LocalTime gapStart = gap.getDateTimeBefore().toLocalTime();
        Fixture fixture = new Fixture(
                fixtureNow(date.minusDays(1)), 0, 10,
                List.of(WeeklyAvailability.create(
                        day, gapStart, gapStart.plusMinutes(90), true)));
        TimeZone previous = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Auckland"));
            var slots = fixture.service.availableSlots(date, date);

            assertThat(slots).allSatisfy(slot ->
                    assertThat(SANTIAGO.getRules().getOffset(slot.startsAt().toInstant()))
                            .isEqualTo(slot.startsAt().getOffset()));
            assertThat(slots).noneMatch(slot ->
                    SANTIAGO.getRules().getValidOffsets(slot.startsAt().toLocalDateTime()).isEmpty());
        } finally {
            TimeZone.setDefault(previous);
        }
    }

    private static LocalDate date(String value) {
        return LocalDate.parse(value);
    }

    private static Instant instant(String value) {
        return java.time.OffsetDateTime.parse(value).toInstant();
    }

    private static Instant fixtureNow(LocalDate localDate) {
        return localDate.atStartOfDay(SANTIAGO).toInstant();
    }

    private static java.time.zone.ZoneOffsetTransition nextGap() {
        var rules = SANTIAGO.getRules();
        var transition = rules.nextTransition(Instant.parse("2026-01-01T00:00:00Z"));
        while (transition != null) {
            if (transition.isGap()) {
                return transition;
            }
            transition = rules.nextTransition(transition.getInstant().plusSeconds(1));
        }
        throw new AssertionError("No Santiago gap found");
    }

    private static AmidogProperties properties(int noticeHours, int horizonDays) {
        return new AmidogProperties(
                SANTIAGO,
                URI.create("http://localhost:5173"),
                URI.create("http://localhost:5173"),
                new AmidogProperties.Booking(false, 30, noticeHours, horizonDays),
                new AmidogProperties.Admin("", "", "", ""),
                new AmidogProperties.Contact("", ""),
                new AmidogProperties.Email("log"));
    }

    @SuppressWarnings("unchecked")
    private static <T> T repository(Class<T> type, Invocation invocation) {
        return (T) Proxy.newProxyInstance(
                type.getClassLoader(), new Class<?>[]{type},
                (proxy, method, args) -> {
                    if (method.getDeclaringClass() == Object.class) {
                        return switch (method.getName()) {
                            case "toString" -> type.getSimpleName() + "Fake";
                            case "hashCode" -> System.identityHashCode(proxy);
                            case "equals" -> proxy == args[0];
                            default -> null;
                        };
                    }
                    return invocation.invoke(method.getName(), args);
                });
    }

    @FunctionalInterface
    private interface Invocation {
        Object invoke(String name, Object[] args);
    }

    private static final class Fixture {
        private final Instant now;
        private final int notice;
        private final int horizon;
        private final List<WeeklyAvailability> weekly;
        private final ClinicTime clinicTime = new ClinicTime(SANTIAGO);
        private List<AvailabilityService.TimeRange> blocks = List.of();
        private List<OccupiedSlotReader.OccupiedSlot> occupied = List.of();
        private int blockQueries;
        private int occupiedQueries;
        private List<Instant> lastBlockRange = new ArrayList<>();
        private AvailabilityService service;

        private Fixture(
                Instant now, int notice, int horizon, List<WeeklyAvailability> weekly) {
            this.now = now;
            this.notice = notice;
            this.horizon = horizon;
            this.weekly = weekly;
            rebuild();
        }

        private AvailabilityService rebuild() {
            WeeklyAvailabilityRepository weeklyRepository = repository(
                    WeeklyAvailabilityRepository.class,
                    (name, args) -> {
                        if (name.equals("findAllByActiveTrueOrderByDayOfWeekAscLocalStartTimeAscLocalEndTimeAsc")) {
                            return weekly;
                        }
                        throw new UnsupportedOperationException(name);
                    });
            AvailabilityBlockRepository blockRepository = repository(
                    AvailabilityBlockRepository.class,
                    (name, args) -> {
                        if (name.equals("findOverlapping")) {
                            blockQueries++;
                            lastBlockRange = List.of((Instant) args[0], (Instant) args[1]);
                            return blocks.stream()
                                    .map(range -> AvailabilityBlock.create(
                                            range.start(), range.end(), null, now))
                                    .toList();
                        }
                        throw new UnsupportedOperationException(name);
                    });
            OccupiedSlotReader occupiedReader = new OccupiedSlotReader(null) {
                @Override
                public List<OccupiedSlot> findOverlapping(Instant start, Instant end) {
                    occupiedQueries++;
                    return occupied;
                }
            };
            service = new AvailabilityService(
                    weeklyRepository,
                    blockRepository,
                    occupiedReader,
                    clinicTime,
                    properties(notice, horizon),
                    Clock.fixed(now, ZoneOffset.UTC));
            return service;
        }
    }
}
