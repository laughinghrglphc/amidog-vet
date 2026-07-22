package com.amidog.app.reservation;

import com.amidog.app.auth.AccountPrincipal;
import com.amidog.app.auth.AccountType;
import com.amidog.app.client.Client;
import com.amidog.app.client.CurrentClient;
import com.amidog.app.common.api.ConflictException;
import com.amidog.app.common.api.ConflictType;
import com.amidog.app.common.api.NotFoundException;
import com.amidog.app.config.AmidogProperties;
import com.amidog.app.notification.NotificationOperations;
import com.amidog.app.scheduling.AvailabilityService;
import com.amidog.app.scheduling.ClinicTime;
import com.amidog.app.scheduling.InvalidSchedulingRequestException;
import com.amidog.app.scheduling.OccupiedSlotReader;
import com.amidog.app.scheduling.SchedulingBadRequestType;
import com.amidog.app.scheduling.SchedulingMutationLock;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;

import static com.amidog.app.reservation.ReservationLifecycleDtos.CancelReservationRequest;
import static com.amidog.app.reservation.ReservationLifecycleDtos.CancellationResponse;
import static com.amidog.app.reservation.ReservationLifecycleDtos.RescheduleRequest;
import static com.amidog.app.reservation.ReservationLifecycleDtos.RescheduleResponse;
import static com.amidog.app.reservation.ReservationLifecycleDtos.StatusChangeRequest;
import static com.amidog.app.reservation.ReservationLifecycleDtos.StatusChangeResponse;

@Service
public class ReservationLifecycleService {

    public static final String RESERVATION_NOT_FOUND =
            "No se encontr\u00f3 la reserva.";

    private final CurrentClient currentClient;
    private final ReservationRepository reservations;
    private final AvailabilityService availability;
    private final ClinicTime clinicTime;
    private final SchedulingMutationLock schedulingLock;
    private final OccupiedSlotReader occupiedSlots;
    private final AmidogProperties properties;
    private final Clock clock;
    private final NotificationOperations notifications;

    public ReservationLifecycleService(
            CurrentClient currentClient,
            ReservationRepository reservations,
            AvailabilityService availability,
            ClinicTime clinicTime,
            SchedulingMutationLock schedulingLock,
            OccupiedSlotReader occupiedSlots,
            AmidogProperties properties,
            Clock clock,
            NotificationOperations notifications) {
        this.currentClient = currentClient;
        this.reservations = reservations;
        this.availability = availability;
        this.clinicTime = clinicTime;
        this.schedulingLock = schedulingLock;
        this.occupiedSlots = occupiedSlots;
        this.properties = properties;
        this.clock = clock;
        this.notifications = notifications;
    }

    @Transactional
    public CancellationResponse cancelClient(
            AccountPrincipal principal,
            long reservationId,
            CancelReservationRequest request) {
        requireVerifiedClient(principal);
        Client client = currentClient.require(principal);
        schedulingLock.acquire();
        Reservation reservation = reservations
                .findOwnedForUpdate(reservationId, client.getId())
                .orElseThrow(this::notFound);
        requireStrictlyFuture(reservation);
        reservation.transitionTo(
                ReservationStatus.CANCELLED,
                ReservationActor.CLIENT,
                request == null ? null : request.reason(),
                clock.instant());
        Reservation persisted = flush(reservation);
        notifications.onClientCancelled(persisted);
        return CancellationResponse.from(persisted, clinicTime);
    }

    @Transactional
    public RescheduleResponse rescheduleClient(
            AccountPrincipal principal,
            long reservationId,
            RescheduleRequest request) {
        requireVerifiedClient(principal);
        Client client = currentClient.require(principal);
        schedulingLock.acquire();
        Reservation reservation = reservations
                .findOwnedForUpdate(reservationId, client.getId())
                .orElseThrow(this::notFound);
        requireStrictlyFuture(reservation);
        ReservationStatus targetStatus =
                properties.booking().autoConfirm()
                        ? ReservationStatus.CONFIRMED
                        : ReservationStatus.PENDING;
        return reschedule(
                reservation,
                requireStart(request),
                targetStatus,
                ReservationActor.CLIENT);
    }

    @Transactional
    public StatusChangeResponse changeStatusAdmin(
            AccountPrincipal principal,
            long reservationId,
            StatusChangeRequest request) {
        requireAdministrator(principal);
        if (request == null || request.status() == null) {
            throw new InvalidSchedulingRequestException(
                    SchedulingBadRequestType
                            .INVALID_RESERVATION_STATUS_CHANGE);
        }
        schedulingLock.acquire();
        Reservation reservation = reservations
                .findByIdForUpdate(reservationId)
                .orElseThrow(this::notFound);
        reservation.transitionTo(
                request.status(),
                ReservationActor.ADMIN,
                request.reason(),
                clock.instant());
        Reservation persisted = flush(reservation);
        if (request.status() == ReservationStatus.CONFIRMED) {
            notifications.onReservationConfirmed(persisted);
        } else if (request.status() == ReservationStatus.CANCELLED) {
            notifications.onAdminCancelled(persisted);
        }
        return StatusChangeResponse.from(persisted, clinicTime);
    }

    @Transactional
    public RescheduleResponse rescheduleAdmin(
            AccountPrincipal principal,
            long reservationId,
            RescheduleRequest request) {
        requireAdministrator(principal);
        schedulingLock.acquire();
        Reservation reservation = reservations
                .findByIdForUpdate(reservationId)
                .orElseThrow(this::notFound);
        ReservationStatus targetStatus = reservation.getStatus()
                == ReservationStatus.CONFIRMED
                ? ReservationStatus.CONFIRMED
                : ReservationStatus.PENDING;
        return reschedule(
                reservation,
                requireStart(request),
                targetStatus,
                ReservationActor.ADMIN);
    }

    private RescheduleResponse reschedule(
            Reservation reservation,
            OffsetDateTime requestedStart,
            ReservationStatus targetStatus,
            ReservationActor actor) {
        requireActive(reservation);
        requireAuthoritativeStart(requestedStart);
        if (reservation.getScheduledStart()
                .equals(requestedStart.toInstant())) {
            throw new ConflictException(
                    ConflictType.RESERVATION_START_UNCHANGED);
        }
        requireAvailable(requestedStart, reservation.getId());
        reservation.reschedule(
                requestedStart.toInstant(),
                targetStatus,
                actor,
                clock.instant());
        Reservation persisted = flush(reservation);
        if (actor == ReservationActor.CLIENT) {
            notifications.onClientRescheduled(persisted);
        } else {
            notifications.onAdminRescheduled(persisted);
        }
        return RescheduleResponse.from(persisted, clinicTime);
    }

    private Reservation flush(Reservation reservation) {
        try {
            return reservations.saveAndFlush(reservation);
        } catch (DataIntegrityViolationException exception) {
            if (ReservationCreationService
                    .isOccupiedSlotCollision(exception)) {
                throw new ConflictException(
                        ConflictType.SLOT_ALREADY_BOOKED);
            }
            throw exception;
        }
    }

    private void requireAvailable(
            OffsetDateTime submitted,
            long excludedReservationId) {
        Instant start = submitted.toInstant();
        Instant end = start.plusSeconds(
                properties.booking().durationMinutes() * 60L);
        boolean availableNow = availability.availableSlots(
                        submitted.toLocalDate(),
                        submitted.toLocalDate(),
                        excludedReservationId)
                .stream()
                .anyMatch(slot ->
                        slot.startsAt().toInstant().equals(start)
                                && slot.endsAt().toInstant().equals(end));
        if (availableNow) {
            return;
        }
        if (!occupiedSlots.findOverlappingExcluding(
                start, end, excludedReservationId).isEmpty()) {
            throw new ConflictException(
                    ConflictType.SLOT_ALREADY_BOOKED);
        }
        throw new ConflictException(ConflictType.SLOT_UNAVAILABLE);
    }

    private void requireAuthoritativeStart(OffsetDateTime submitted) {
        if (submitted == null) {
            throw new InvalidSchedulingRequestException(
                    SchedulingBadRequestType.INVALID_RESERVATION_START);
        }
        var authoritative =
                clinicTime.resolve(submitted.toLocalDateTime());
        if (!authoritative.getOffset().equals(submitted.getOffset())
                || !authoritative.toInstant()
                        .equals(submitted.toInstant())) {
            throw new InvalidSchedulingRequestException(
                    SchedulingBadRequestType.INVALID_RESERVATION_START);
        }
    }

    private static OffsetDateTime requireStart(
            RescheduleRequest request) {
        if (request == null || request.startsAt() == null) {
            throw new InvalidSchedulingRequestException(
                    SchedulingBadRequestType.INVALID_RESERVATION_START);
        }
        return request.startsAt();
    }

    private void requireStrictlyFuture(Reservation reservation) {
        if (!reservation.getScheduledStart()
                .isAfter(clock.instant())) {
            throw new ConflictException(
                    ConflictType.RESERVATION_NOT_IN_FUTURE);
        }
    }

    private static void requireActive(Reservation reservation) {
        if (reservation.getStatus() != ReservationStatus.PENDING
                && reservation.getStatus()
                        != ReservationStatus.CONFIRMED) {
            throw new ConflictException(
                    ConflictType.INVALID_RESERVATION_STATUS_TRANSITION);
        }
    }

    private static void requireVerifiedClient(
            AccountPrincipal principal) {
        if (principal == null
                || principal.getAccountType() != AccountType.CLIENT
                || !principal.isEnabled()) {
            throw new AccessDeniedException(
                    "Verified client required");
        }
    }

    private static void requireAdministrator(
            AccountPrincipal principal) {
        if (principal == null
                || principal.getAccountType() != AccountType.ADMIN
                || !principal.isEnabled()) {
            throw new AccessDeniedException(
                    "Administrator required");
        }
    }

    private NotFoundException notFound() {
        return new NotFoundException(RESERVATION_NOT_FOUND);
    }
}
