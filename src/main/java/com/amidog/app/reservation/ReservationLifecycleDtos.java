package com.amidog.app.reservation;

import com.amidog.app.scheduling.ClinicTime;
import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;

public final class ReservationLifecycleDtos {

    private ReservationLifecycleDtos() {
    }

    public record CancelReservationRequest(
            @NormalizedTextLength(max = 300) String reason) {
    }

    public record RescheduleRequest(
            @NotNull OffsetDateTime startsAt) {
    }

    public record StatusChangeRequest(
            @NotNull ReservationStatus status,
            @NormalizedTextLength(max = 300) String reason) {
    }

    public record CancellationResponse(
            Long id,
            ReservationStatus status,
            OffsetDateTime startsAt,
            OffsetDateTime cancelledAt,
            ReservationActor cancelledBy,
            String reason,
            OffsetDateTime updatedAt) {
        static CancellationResponse from(
                Reservation reservation,
                ClinicTime clinicTime) {
            return new CancellationResponse(
                    reservation.getId(),
                    reservation.getStatus(),
                    clinicTime.toOffsetDateTime(
                            reservation.getScheduledStart()),
                    clinicTime.toOffsetDateTime(
                            reservation.getCancelledAt()),
                    ReservationActor.valueOf(
                            reservation.getCancelledBy()),
                    reservation.getCancellationReason(),
                    clinicTime.toOffsetDateTime(
                            reservation.getUpdatedAt()));
        }
    }

    public record RescheduleResponse(
            Long id,
            OffsetDateTime previousStartsAt,
            OffsetDateTime startsAt,
            OffsetDateTime endsAt,
            ReservationStatus previousStatus,
            ReservationStatus status,
            OffsetDateTime updatedAt) {
        static RescheduleResponse from(
                Reservation reservation,
                ClinicTime clinicTime) {
            ReservationEvent event = reservation
                    .getEvents().getLast();
            return new RescheduleResponse(
                    reservation.getId(),
                    clinicTime.toOffsetDateTime(
                            event.getPreviousStart()),
                    clinicTime.toOffsetDateTime(
                            reservation.getScheduledStart()),
                    clinicTime.toOffsetDateTime(
                            reservation.getScheduledEnd()),
                    event.getPreviousStatus(),
                    reservation.getStatus(),
                    clinicTime.toOffsetDateTime(
                            reservation.getUpdatedAt()));
        }
    }

    public record StatusChangeResponse(
            Long id,
            ReservationStatus previousStatus,
            ReservationStatus status,
            String reason,
            OffsetDateTime updatedAt) {
        static StatusChangeResponse from(
                Reservation reservation,
                ClinicTime clinicTime) {
            ReservationEvent event = reservation
                    .getEvents().getLast();
            return new StatusChangeResponse(
                    reservation.getId(),
                    event.getPreviousStatus(),
                    reservation.getStatus(),
                    event.getReason(),
                    clinicTime.toOffsetDateTime(
                            reservation.getUpdatedAt()));
        }
    }
}
