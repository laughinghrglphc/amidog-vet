package com.amidog.app.admin;

import com.amidog.app.auth.AccountPrincipal;
import com.amidog.app.auth.AccountType;
import com.amidog.app.config.AmidogProperties;
import com.amidog.app.scheduling.ClinicTime;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.TimeZone;

import static com.amidog.app.admin.AdminDtos.AdminReservationSummary;
import static com.amidog.app.admin.AdminDtos.DashboardStats;
import static com.amidog.app.admin.AdminDtos.ReservationItemSummary;
import static org.assertj.core.api.Assertions.assertThat;

class AdminDashboardServiceTests {

    private static final Instant NOW =
            Instant.parse("2026-04-05T14:00:00Z");

    @Test
    void buildsAProfileWithoutLookingForAnAdministratorClientRow() {
        FakeAdminReadRepository reads = new FakeAdminReadRepository();
        AdminDashboardService service = service(reads, NOW);

        var response = service.dashboard(admin());

        assertThat(response.profile().name()).isEqualTo("Nataly Apablaza");
        assertThat(response.profile().email()).isEqualTo("admin@amidog.cl");
        assertThat(response.profile().role()).isEqualTo("ADMIN");
        assertThat(reads.dashboardRanges).containsExactly(new InstantRange(
                Instant.parse("2026-04-05T04:00:00Z"),
                Instant.parse("2026-04-06T04:00:00Z")));
    }

    @Test
    void usesBoundedUpcomingAndTodayReadsAcrossChileDstRegardlessOfJvmZone() {
        FakeAdminReadRepository reads = new FakeAdminReadRepository();
        reads.upcoming = List.of(summary(7, "2026-04-05T14:30:00Z"));
        reads.today = List.of(summary(8, "2026-04-05T15:00:00Z"));
        TimeZone original = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"));
        try {
            var response = service(reads, NOW).dashboard(admin());

            assertThat(reads.upcomingLimit).isEqualTo(10);
            assertThat(reads.todayLimit).isEqualTo(100);
        assertThat(response.appointments()).extracting(AdminReservationSummary::id)
                    .containsExactly(7L);
            assertThat(response.schedule()).extracting(item -> item.reservationId())
                    .containsExactly(8L);
            assertThat(response.appointments().getFirst().startsAt().toString())
                    .isEqualTo("2026-04-05T10:30-04:00");
        } finally {
            TimeZone.setDefault(original);
        }
    }

    @Test
    void allocatesDeterministicWholePercentagesFromCurrentMonthSnapshots() {
        FakeAdminReadRepository reads = new FakeAdminReadRepository();
        reads.usage = List.of(
                new AdminReadRepository.ServiceUsageCount("Consulta", 1),
                new AdminReadRepository.ServiceUsageCount("Vacuna", 1),
                new AdminReadRepository.ServiceUsageCount("Control", 1));

        var services = service(reads, NOW).dashboard(admin()).services();

        assertThat(services).extracting(
                        value -> value.name() + ":" + value.count() + ":"
                                + value.percentage())
                .containsExactly(
                        "Consulta:1:34",
                        "Control:1:33",
                        "Vacuna:1:33");
        assertThat(reads.usageRanges).containsExactly(new InstantRange(
                Instant.parse("2026-04-01T03:00:00Z"),
                Instant.parse("2026-05-01T04:00:00Z")));
    }

    @Test
    void returnsEmptyUsageAndZeroFilledCurrentPreviousWeekAndMonthBuckets() {
        FakeAdminReadRepository reads = new FakeAdminReadRepository();
        reads.dailyCounts = List.of(
                new AdminReadRepository.DailyReservationCount(
                        LocalDate.of(2026, 3, 23), 2),
                new AdminReadRepository.DailyReservationCount(
                        LocalDate.of(2026, 4, 5), 3),
                new AdminReadRepository.DailyReservationCount(
                        LocalDate.of(2026, 4, 15), 4));

        var response = service(reads, NOW).dashboard(admin());

        assertThat(response.services()).isEmpty();
        assertThat(response.chart().currentWeek().labels())
                .containsExactly("Lun", "Mar", "Mi\u00e9", "Jue", "Vie", "S\u00e1b", "Dom");
        assertThat(response.chart().currentWeek().values())
                .containsExactly(0L, 0L, 0L, 0L, 0L, 0L, 3L);
        assertThat(response.chart().previousWeek().values())
                .containsExactly(2L, 0L, 0L, 0L, 0L, 0L, 0L);
        assertThat(response.chart().currentMonth().labels())
                .containsExactly("Sem 1", "Sem 2", "Sem 3", "Sem 4", "Sem 5");
        assertThat(response.chart().currentMonth().values())
                .containsExactly(3L, 0L, 4L, 0L, 0L);
        assertThat(reads.dailyRanges).containsExactly(new InstantRange(
                Instant.parse("2026-03-23T03:00:00Z"),
                Instant.parse("2026-05-01T04:00:00Z")));
    }

    @Test
    void boundsEveryDashboardCollectionEvenIfAStoreReturnsExtraRows() {
        FakeAdminReadRepository reads = new FakeAdminReadRepository();
        reads.upcoming = java.util.stream.LongStream.rangeClosed(1, 12)
                .mapToObj(id -> summary(
                        id, "2026-04-06T14:00:00Z"))
                .toList();
        reads.today = java.util.stream.LongStream.rangeClosed(1, 102)
                .mapToObj(id -> summary(
                        id, "2026-04-05T14:30:00Z"))
                .toList();
        reads.usage = java.util.stream.LongStream.rangeClosed(1, 12)
                .mapToObj(id -> new AdminReadRepository.ServiceUsageCount(
                        "Servicio " + id, 1))
                .toList();

        var response = service(reads, NOW).dashboard(admin());

        assertThat(response.appointments()).hasSize(10);
        assertThat(response.schedule()).hasSize(100);
        assertThat(response.services()).hasSize(10);
    }

    private static AdminDashboardService service(
            FakeAdminReadRepository reads,
            Instant now) {
        AmidogProperties properties = properties();
        return new AdminDashboardService(
                reads,
                new ClinicTime(properties),
                properties,
                Clock.fixed(now, ZoneId.of("UTC")));
    }

    private static AccountPrincipal admin() {
        return new AccountPrincipal(
                1L, null, "admin@amidog.cl", "Admin",
                AccountType.ADMIN, "{noop}secret", true, true);
    }

    private static AmidogProperties properties() {
        return new AmidogProperties(
                ZoneId.of("America/Santiago"),
                URI.create("http://localhost"),
                URI.create("http://localhost"),
                new AmidogProperties.Booking(false, 30, 2, 90),
                new AmidogProperties.Admin(
                        "admin@amidog.cl", "secret",
                        "Nataly Apablaza", "+56900000000"),
                new AmidogProperties.Contact("", ""),
                new AmidogProperties.Email("log"));
    }

    private static AdminReadRepository.ReservationRecord summary(
            long id,
            String start) {
        Instant startsAt = Instant.parse(start);
        return new AdminReadRepository.ReservationRecord(
                id, 10L, "Ana", "ana@example.com", "+56911111111",
                startsAt, startsAt.plusSeconds(1800),
                com.amidog.app.reservation.ReservationStatus.CONFIRMED,
                null, startsAt.minusSeconds(3600), startsAt.minusSeconds(3600),
                List.of(new ReservationItemSummary(
                        20L, "Luna", "Perro", "Mestiza",
                        30L, "Consulta")));
    }

    private record InstantRange(Instant start, Instant end) {
    }

    private static final class FakeAdminReadRepository
            implements AdminReadRepository {

        private final List<InstantRange> dashboardRanges = new ArrayList<>();
        private final List<InstantRange> usageRanges = new ArrayList<>();
        private final List<InstantRange> dailyRanges = new ArrayList<>();
        private List<ReservationRecord> upcoming = List.of();
        private List<ReservationRecord> today = List.of();
        private List<ServiceUsageCount> usage = List.of();
        private List<DailyReservationCount> dailyCounts = List.of();
        private int upcomingLimit;
        private int todayLimit;

        @Override
        public DashboardStats dashboardStats(
                Instant todayStart,
                Instant tomorrowStart) {
            dashboardRanges.add(new InstantRange(
                    todayStart, tomorrowStart));
            return new DashboardStats(0, 0, 0);
        }

        @Override
        public List<ReservationRecord> upcomingReservations(
                Instant fromInclusive,
                int limit) {
            upcomingLimit = limit;
            return upcoming;
        }

        @Override
        public List<ReservationRecord> reservationsBetween(
                Instant fromInclusive,
                Instant toExclusive,
                int limit) {
            todayLimit = limit;
            return today;
        }

        @Override
        public List<ServiceUsageCount> serviceUsage(
                Instant fromInclusive,
                Instant toExclusive) {
            usageRanges.add(new InstantRange(
                    fromInclusive, toExclusive));
            return usage;
        }

        @Override
        public List<DailyReservationCount> dailyReservationCounts(
                Instant fromInclusive,
                Instant toExclusive) {
            dailyRanges.add(new InstantRange(
                    fromInclusive, toExclusive));
            return dailyCounts;
        }

        @Override
        public PageResponse<AdminReservationSummary> reservations(
                ReservationQuery query) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<AdminDtos.AdminReservationDetail> reservation(
                long id) {
            throw new UnsupportedOperationException();
        }

        @Override
        public PageResponse<AdminDtos.AdminClientSummary> clients(
                ClientQuery query) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<AdminDtos.AdminClientDetail> client(long id) {
            throw new UnsupportedOperationException();
        }

        @Override
        public PageResponse<AdminDtos.AdminPetSummary> pets(
                PetQuery query) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<AdminDtos.AdminPetDetail> pet(long id) {
            throw new UnsupportedOperationException();
        }
    }
}
