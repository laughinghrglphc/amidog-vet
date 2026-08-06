package com.amidog.app.reservation;

import com.amidog.app.scheduling.ClinicTime;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.List;

public final class ReservationDtos {

    private ReservationDtos() {
    }

    public record CreateReservationRequest(
            @NotNull OffsetDateTime startsAt,
            @NotEmpty @Size(max = 10)
            List<@NotNull @Valid ReservationItemRequest> items,
            @Size(max = 500) String note) {
    }

    public record ReservationItemRequest(
            @NotNull Long petId,
            @NotNull Long serviceId) {
    }

    public record ReservationItemResponse(
            Long petId,
            String petName,
            Long serviceId,
            String serviceName) {
        static ReservationItemResponse from(ReservationItem item) {
            return new ReservationItemResponse(
                    item.getPet().getId(),
                    item.getPet().getName(),
                    item.getService().getId(),
                    item.getServiceNameSnapshot());
        }
    }

    public record ReservationResponse(
            Long id,
            OffsetDateTime startsAt,
            OffsetDateTime endsAt,
            ReservationStatus status,
            String note,
            List<ReservationItemResponse> items,
            OffsetDateTime createdAt) {
        static ReservationResponse from(
                Reservation reservation, ClinicTime clinicTime) {
            return new ReservationResponse(
                    reservation.getId(),
                    clinicTime.toOffsetDateTime(
                            reservation.getScheduledStart()),
                    clinicTime.toOffsetDateTime(
                            reservation.getScheduledEnd()),
                    reservation.getStatus(),
                    reservation.getClientNote(),
                    reservation.getItems().stream()
                            .map(ReservationItemResponse::from)
                            .toList(),
                    clinicTime.toOffsetDateTime(
                            reservation.getCreatedAt()));
        }
    }
}
