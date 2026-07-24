package com.amidog.app.admin;

import com.amidog.app.reservation.ReservationStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static com.amidog.app.admin.AdminDtos.AdminClientDetail;
import static com.amidog.app.admin.AdminDtos.AdminClientSummary;
import static com.amidog.app.admin.AdminDtos.AdminPetDetail;
import static com.amidog.app.admin.AdminDtos.AdminPetSummary;
import static com.amidog.app.admin.AdminDtos.AdminReservationDetail;
import static com.amidog.app.admin.AdminDtos.AdminReservationSummary;
import static com.amidog.app.admin.AdminDtos.DashboardStats;
import static com.amidog.app.admin.AdminDtos.ReservationItemSummary;

public interface AdminReadRepository {

    DashboardStats dashboardStats(
            Instant todayStart,
            Instant tomorrowStart);

    List<ReservationRecord> upcomingReservations(
            Instant fromInclusive,
            int limit);

    List<ReservationRecord> reservationsBetween(
            Instant fromInclusive,
            Instant toExclusive,
            int limit);

    List<ServiceUsageCount> serviceUsage(
            Instant fromInclusive,
            Instant toExclusive);

    List<DailyReservationCount> dailyReservationCounts(
            Instant fromInclusive,
            Instant toExclusive);

    PageResponse<AdminReservationSummary> reservations(
            ReservationQuery query);

    Optional<AdminReservationDetail> reservation(long id);

    PageResponse<AdminClientSummary> clients(ClientQuery query);

    Optional<AdminClientDetail> client(long id);

    PageResponse<AdminPetSummary> pets(PetQuery query);

    Optional<AdminPetDetail> pet(long id);

    record ReservationRecord(
            Long id,
            Long clientId,
            String clientName,
            String clientEmail,
            String clientPhone,
            Instant startsAt,
            Instant endsAt,
            ReservationStatus status,
            String clientNote,
            Instant createdAt,
            Instant updatedAt,
            List<ReservationItemSummary> items) {
        public ReservationRecord {
            items = List.copyOf(items);
        }
    }

    record ServiceUsageCount(String name, long count) {
    }

    record DailyReservationCount(LocalDate date, long count) {
    }

    record ReservationQuery(
            Instant fromInclusive,
            Instant toExclusive,
            ReservationStatus status,
            String searchPattern,
            int page,
            int size) {
    }

    record ClientQuery(
            String searchPattern,
            Boolean active,
            int page,
            int size) {
    }

    record PetQuery(
            String searchPattern,
            Boolean active,
            Long clientId,
            int page,
            int size) {
    }
}
