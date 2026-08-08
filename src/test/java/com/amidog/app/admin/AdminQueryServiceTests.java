package com.amidog.app.admin;

import com.amidog.app.auth.AccountPrincipal;
import com.amidog.app.auth.AccountType;
import com.amidog.app.common.api.NotFoundException;
import com.amidog.app.config.AmidogProperties;
import com.amidog.app.reservation.ReservationStatus;
import com.amidog.app.scheduling.ClinicTime;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AdminQueryServiceTests {

    @Test
    void validatesAndPassesACombinedReservationFilterToTheDatabaseStore() {
        FakeReads reads = new FakeReads();
        AdminQueryService service = service(reads);

        service.reservations(
                admin(),
                LocalDate.of(2026, 8, 10),
                LocalDate.of(2026, 8, 11),
                ReservationStatus.CONFIRMED,
                " Ana%_! ",
                2,
                25);

        assertThat(reads.reservationQuery)
                .isEqualTo(new AdminReadRepository.ReservationQuery(
                        Instant.parse("2026-08-10T04:00:00Z"),
                        Instant.parse("2026-08-12T04:00:00Z"),
                        ReservationStatus.CONFIRMED,
                        "%ana!%!_!!%",
                        2,
                        25));
    }

    @Test
    void passesClientAndPetFiltersWithoutLoadingTablesInTheService() {
        FakeReads reads = new FakeReads();
        AdminQueryService service = service(reads);

        service.clients(admin(), " María ", false, 1, 20);
        service.pets(admin(), " Luna ", true, 42L, 3, 10);

        assertThat(reads.clientQuery)
                .isEqualTo(new AdminReadRepository.ClientQuery(
                        "%maría%", false, 1, 20));
        assertThat(reads.petQuery)
                .isEqualTo(new AdminReadRepository.PetQuery(
                        "%luna%", true, 42L, 3, 10));
    }

    @Test
    void usesGenericNotFoundResponsesForEveryDetailType() {
        FakeReads reads = new FakeReads();
        AdminQueryService service = service(reads);

        assertThatThrownBy(() -> service.reservation(admin(), 1))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("No se encontr\u00f3 la reserva.");
        assertThatThrownBy(() -> service.client(admin(), 2))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("No se encontr\u00f3 el cliente.");
        assertThatThrownBy(() -> service.pet(admin(), 3))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("No se encontr\u00f3 la mascota.");
    }

    private static AdminQueryService service(FakeReads reads) {
        AmidogProperties properties = new AmidogProperties(
                ZoneId.of("America/Santiago"),
                URI.create("http://localhost"),
                URI.create("http://localhost"),
                new AmidogProperties.Booking(false, 30, 2, 90),
                new AmidogProperties.Admin("", "", "Admin", ""),
                new AmidogProperties.Contact("", ""),
                new AmidogProperties.Email("log"));
        ClinicTime clinicTime = new ClinicTime(properties);
        return new AdminQueryService(
                reads,
                new AdminRequestValidator(clinicTime));
    }

    private static AccountPrincipal admin() {
        return new AccountPrincipal(
                1L, null, "admin@amidog.cl", "Admin",
                AccountType.ADMIN, "{noop}secret", true, true);
    }

    private static final class FakeReads
            implements AdminReadRepository {

        private ReservationQuery reservationQuery;
        private ClientQuery clientQuery;
        private PetQuery petQuery;

        @Override
        public PageResponse<AdminDtos.AdminReservationSummary> reservations(
                ReservationQuery query) {
            reservationQuery = query;
            return PageResponse.of(
                    List.of(), query.page(), query.size(), 0);
        }

        @Override
        public Optional<AdminDtos.AdminReservationDetail> reservation(
                long id) {
            return Optional.empty();
        }

        @Override
        public PageResponse<AdminDtos.AdminClientSummary> clients(
                ClientQuery query) {
            clientQuery = query;
            return PageResponse.of(
                    List.of(), query.page(), query.size(), 0);
        }

        @Override
        public Optional<AdminDtos.AdminClientDetail> client(long id) {
            return Optional.empty();
        }

        @Override
        public PageResponse<AdminDtos.AdminPetSummary> pets(
                PetQuery query) {
            petQuery = query;
            return PageResponse.of(
                    List.of(), query.page(), query.size(), 0);
        }

        @Override
        public Optional<AdminDtos.AdminPetDetail> pet(long id) {
            return Optional.empty();
        }

        @Override
        public AdminDtos.DashboardStats dashboardStats(
                Instant todayStart,
                Instant tomorrowStart) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<ReservationRecord> upcomingReservations(
                Instant fromInclusive,
                int limit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<ReservationRecord> reservationsBetween(
                Instant fromInclusive,
                Instant toExclusive,
                int limit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<ServiceUsageCount> serviceUsage(
                Instant fromInclusive,
                Instant toExclusive) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<DailyReservationCount> dailyReservationCounts(
                Instant fromInclusive,
                Instant toExclusive) {
            throw new UnsupportedOperationException();
        }
    }
}
