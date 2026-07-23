package com.amidog.app.admin;

import com.amidog.app.reservation.ReservationActor;
import com.amidog.app.reservation.ReservationEventType;
import com.amidog.app.reservation.ReservationStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public final class AdminDtos {

    private AdminDtos() {
    }

    public record ReservationItemSummary(
            Long petId,
            String petName,
            String species,
            String breed,
            Long serviceId,
            String serviceName) {
    }

    public record AdminReservationSummary(
            Long id,
            Long clientId,
            String clientName,
            String clientEmail,
            String clientPhone,
            OffsetDateTime startsAt,
            OffsetDateTime endsAt,
            ReservationStatus status,
            String clientNote,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt,
            List<ReservationItemSummary> items) {
        public AdminReservationSummary {
            items = List.copyOf(items);
        }
    }

    public record ReservationEventSummary(
            Long id,
            ReservationEventType eventType,
            ReservationActor actor,
            ReservationStatus previousStatus,
            ReservationStatus newStatus,
            OffsetDateTime previousStartsAt,
            OffsetDateTime newStartsAt,
            String reason,
            OffsetDateTime createdAt) {
    }

    public record AdminReservationDetail(
            Long id,
            Long clientId,
            String clientName,
            String clientEmail,
            String clientPhone,
            OffsetDateTime startsAt,
            OffsetDateTime endsAt,
            ReservationStatus status,
            String clientNote,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt,
            OffsetDateTime cancelledAt,
            CancellationActor cancelledBy,
            String cancellationReason,
            List<ReservationItemSummary> items,
            List<ReservationEventSummary> events) {
        public AdminReservationDetail {
            items = List.copyOf(items);
            events = List.copyOf(events);
        }
    }

    /**
     * Closed read-model values accepted in the frozen database. MIGRATION is
     * deliberately absent from ReservationActor, so it cannot be supplied to
     * new lifecycle transitions or reschedules.
     */
    public enum CancellationActor {
        CLIENT,
        ADMIN,
        MIGRATION;

        public static CancellationActor fromStored(String value) {
            return value == null
                    ? null
                    : CancellationActor.valueOf(value);
        }
    }

    public record AdminClientSummary(
            Long id,
            String name,
            String email,
            String phone,
            boolean active,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt) {
    }

    public record ClientPetSummary(
            Long id,
            String name,
            String species,
            String breed,
            LocalDate birthdate,
            boolean active) {
    }

    public record ReservationCounts(
            long total,
            long upcoming,
            long pending,
            long confirmed,
            long cancelled,
            long completed,
            long noShow) {
    }

    public record AdminClientDetail(
            Long id,
            String name,
            String email,
            String phone,
            boolean active,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt,
            List<ClientPetSummary> pets,
            ReservationCounts reservationCounts,
            List<AdminReservationSummary> upcomingReservations) {
        public AdminClientDetail {
            pets = List.copyOf(pets);
            upcomingReservations = List.copyOf(upcomingReservations);
        }
    }

    public record AdminPetSummary(
            Long id,
            Long clientId,
            String ownerName,
            String name,
            String species,
            String breed,
            LocalDate birthdate,
            boolean active,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt) {
    }

    public record AdminPetDetail(
            Long id,
            Long clientId,
            String ownerName,
            String ownerEmail,
            String name,
            String species,
            String breed,
            LocalDate birthdate,
            boolean active,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt,
            List<AdminReservationSummary> recentReservations) {
        public AdminPetDetail {
            recentReservations = List.copyOf(recentReservations);
        }
    }

    public record AdminProfile(
            String name,
            String email,
            String role) {
    }

    public record DashboardStats(
            long todayAppointments,
            long clients,
            long pets) {
    }

    public record ServiceUsage(
            String name,
            long count,
            int percentage) {
    }

    public record ChartSeries(
            String label,
            List<String> labels,
            List<Long> values) {
        public ChartSeries {
            labels = List.copyOf(labels);
            values = List.copyOf(values);
        }
    }

    public record DashboardChart(
            ChartSeries currentWeek,
            ChartSeries previousWeek,
            ChartSeries currentMonth) {
    }

    public record AgendaItem(
            Long reservationId,
            OffsetDateTime startsAt,
            OffsetDateTime endsAt,
            ReservationStatus status,
            List<ReservationItemSummary> items) {
        public AgendaItem {
            items = List.copyOf(items);
        }
    }

    public record DashboardResponse(
            AdminProfile profile,
            DashboardStats stats,
            List<AdminReservationSummary> appointments,
            List<ServiceUsage> services,
            DashboardChart chart,
            List<AgendaItem> schedule) {
        public DashboardResponse {
            appointments = List.copyOf(appointments);
            services = List.copyOf(services);
            schedule = List.copyOf(schedule);
        }
    }

    public record AdminClientUpdateRequest(
            @NotBlank @Size(max = 120) String name,
            @NotBlank @Size(max = 30) String phone,
            @NotNull Boolean active) {
    }

    public record AdminPetUpdateRequest(
            @NotBlank @Size(max = 80) String name,
            @NotBlank @Size(max = 40) String species,
            @Size(max = 80) String breed,
            @PastOrPresent LocalDate birthdate) {
    }
}
