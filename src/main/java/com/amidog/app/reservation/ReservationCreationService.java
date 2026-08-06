package com.amidog.app.reservation;

import com.amidog.app.auth.AccountPrincipal;
import com.amidog.app.auth.AccountType;
import com.amidog.app.catalog.ServiceOffering;
import com.amidog.app.catalog.ServiceOfferingRepository;
import com.amidog.app.client.Client;
import com.amidog.app.client.CurrentClient;
import com.amidog.app.client.Pet;
import com.amidog.app.client.PetRepository;
import com.amidog.app.common.api.ConflictException;
import com.amidog.app.common.api.ConflictType;
import com.amidog.app.common.api.NotFoundException;
import com.amidog.app.common.api.TooManyRequestsException;
import com.amidog.app.common.security.RateLimitService;
import com.amidog.app.config.AmidogProperties;
import com.amidog.app.notification.NotificationOperations;
import com.amidog.app.scheduling.AvailabilityService;
import com.amidog.app.scheduling.ClinicTime;
import com.amidog.app.scheduling.InvalidSchedulingRequestException;
import com.amidog.app.scheduling.OccupiedSlotReader;
import com.amidog.app.scheduling.SchedulingBadRequestType;
import com.amidog.app.scheduling.SchedulingMutationLock;
import org.postgresql.util.PSQLException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.amidog.app.reservation.ReservationDtos.CreateReservationRequest;
import static com.amidog.app.reservation.ReservationDtos.ReservationItemRequest;
import static com.amidog.app.reservation.ReservationDtos.ReservationResponse;

@Service
public class ReservationCreationService {

    static final String PET_NOT_FOUND =
            "No se encontr\u00f3 la mascota.";
    static final String SERVICE_NOT_FOUND =
            "No se encontr\u00f3 el servicio.";
    private static final String EXCLUSION_STATE = "23P01";
    private static final String OCCUPIED_CONSTRAINT =
            "reservations_no_occupied_overlap";
    private static final int CREATION_LIMIT = 10;
    private static final Duration CREATION_WINDOW = Duration.ofHours(1);

    private final CurrentClient currentClient;
    private final ReservationRepository reservations;
    private final PetRepository pets;
    private final ServiceOfferingRepository services;
    private final AvailabilityService availability;
    private final ClinicTime clinicTime;
    private final SchedulingMutationLock schedulingLock;
    private final OccupiedSlotReader occupiedSlots;
    private final AmidogProperties properties;
    private final Clock clock;
    private final RateLimitService rateLimits;
    private final NotificationOperations notifications;

    public ReservationCreationService(
            CurrentClient currentClient,
            ReservationRepository reservations,
            PetRepository pets,
            ServiceOfferingRepository services,
            AvailabilityService availability,
            ClinicTime clinicTime,
            SchedulingMutationLock schedulingLock,
            OccupiedSlotReader occupiedSlots,
            AmidogProperties properties,
            Clock clock,
            RateLimitService rateLimits,
            NotificationOperations notifications) {
        this.currentClient = currentClient;
        this.reservations = reservations;
        this.pets = pets;
        this.services = services;
        this.availability = availability;
        this.clinicTime = clinicTime;
        this.schedulingLock = schedulingLock;
        this.occupiedSlots = occupiedSlots;
        this.properties = properties;
        this.clock = clock;
        this.rateLimits = rateLimits;
        this.notifications = notifications;
    }

    @Transactional
    public ReservationResponse create(
            AccountPrincipal principal, CreateReservationRequest request) {
        requireVerifiedClient(principal);
        if (!rateLimits.tryAcquire(
                "reservation:create:user:" + principal.getUserId(),
                CREATION_LIMIT,
                CREATION_WINDOW)) {
            throw new TooManyRequestsException();
        }
        Client client = currentClient.require(principal);
        validateItems(request);

        Map<Long, Pet> ownedPets =
                lockOwnedPets(client.getId(), request.items());
        Map<Long, ServiceOffering> activeServices =
                lockActiveServices(request.items());
        requireAuthoritativeStart(request.startsAt());
        requireAvailable(request.startsAt());

        schedulingLock.acquire();
        requireAvailable(request.startsAt());

        Instant now = clock.instant();
        ReservationStatus status = properties.booking().autoConfirm()
                ? ReservationStatus.CONFIRMED
                : ReservationStatus.PENDING;
        Reservation reservation = Reservation.create(
                client,
                request.startsAt().toInstant(),
                status,
                request.note(),
                now);
        request.items().forEach(item -> reservation.addItem(
                ownedPets.get(item.petId()),
                activeServices.get(item.serviceId())));
        reservation.addCreationEvent(now);

        Reservation persisted;
        try {
            persisted = reservations.saveAndFlush(reservation);
        } catch (DataIntegrityViolationException exception) {
            if (isOccupiedSlotCollision(exception)) {
                throw new ConflictException(
                        ConflictType.SLOT_ALREADY_BOOKED);
            }
            throw exception;
        }
        notifications.onReservationCreated(persisted);
        return ReservationResponse.from(persisted, clinicTime);
    }

    @Transactional(readOnly = true)
    public List<ReservationResponse> list(AccountPrincipal principal) {
        requireVerifiedClient(principal);
        Client client = currentClient.require(principal);
        return reservations
                .findAllByClientIdOrderByScheduledStartDescIdDesc(
                        client.getId())
                .stream()
                .map(reservation ->
                        ReservationResponse.from(reservation, clinicTime))
                .toList();
    }

    private void validateItems(CreateReservationRequest request) {
        if (request == null || request.startsAt() == null
                || request.items() == null
                || request.items().isEmpty()
                || request.items().size() > 10
                || request.items().stream().anyMatch(item ->
                        item == null
                                || item.petId() == null
                                || item.serviceId() == null)) {
            throw new InvalidSchedulingRequestException(
                    SchedulingBadRequestType.INVALID_RESERVATION_ITEMS);
        }
        Set<Long> distinctPets = new java.util.HashSet<>();
        if (request.items().stream()
                .map(ReservationItemRequest::petId)
                .anyMatch(id -> !distinctPets.add(id))) {
            throw new InvalidSchedulingRequestException(
                    SchedulingBadRequestType.DUPLICATE_RESERVATION_PET);
        }
    }

    private Map<Long, Pet> lockOwnedPets(
            long clientId, List<ReservationItemRequest> items) {
        Map<Long, Pet> result = new LinkedHashMap<>();
        items.stream()
                .map(ReservationItemRequest::petId)
                .distinct()
                .sorted()
                .forEach(petId -> result.put(
                        petId,
                        pets.findActiveOwnedForUpdate(petId, clientId)
                                .orElseThrow(() ->
                                        new NotFoundException(PET_NOT_FOUND))));
        return result;
    }

    private Map<Long, ServiceOffering> lockActiveServices(
            List<ReservationItemRequest> items) {
        Map<Long, ServiceOffering> result = new LinkedHashMap<>();
        items.stream()
                .map(ReservationItemRequest::serviceId)
                .distinct()
                .sorted()
                .forEach(serviceId -> {
                    ServiceOffering service =
                            services.findByIdForUpdate(serviceId)
                                    .filter(ServiceOffering::isActive)
                                    .orElseThrow(() ->
                                            new NotFoundException(
                                                    SERVICE_NOT_FOUND));
                    result.put(serviceId, service);
                });
        return result;
    }

    private void requireAuthoritativeStart(OffsetDateTime submitted) {
        var authoritative =
                clinicTime.resolve(submitted.toLocalDateTime());
        if (!authoritative.getOffset().equals(submitted.getOffset())
                || !authoritative.toInstant().equals(submitted.toInstant())) {
            throw new InvalidSchedulingRequestException(
                    SchedulingBadRequestType.INVALID_RESERVATION_START);
        }
    }

    private void requireAvailable(OffsetDateTime submitted) {
        Instant start = submitted.toInstant();
        Instant end = start.plusSeconds(
                properties.booking().durationMinutes() * 60L);
        boolean availableNow = availability.availableSlots(
                        submitted.toLocalDate(), submitted.toLocalDate())
                .stream()
                .anyMatch(slot ->
                        slot.startsAt().toInstant().equals(start)
                                && slot.endsAt().toInstant().equals(end));
        if (availableNow) {
            return;
        }
        if (!occupiedSlots.findOverlapping(start, end).isEmpty()) {
            throw new ConflictException(
                    ConflictType.SLOT_ALREADY_BOOKED);
        }
        throw new ConflictException(ConflictType.SLOT_UNAVAILABLE);
    }

    private static void requireVerifiedClient(AccountPrincipal principal) {
        if (principal == null
                || principal.getAccountType() != AccountType.CLIENT
                || !principal.isEnabled()) {
            throw new AccessDeniedException("Verified client required");
        }
    }

    static boolean isOccupiedSlotCollision(Throwable failure) {
        Set<Throwable> seen =
                Collections.newSetFromMap(new IdentityHashMap<>());
        Throwable current = failure;
        while (current != null && seen.add(current)) {
            if (current instanceof PSQLException postgres
                    && EXCLUSION_STATE.equals(postgres.getSQLState())
                    && postgres.getServerErrorMessage() != null
                    && OCCUPIED_CONSTRAINT.equals(
                            postgres.getServerErrorMessage()
                                    .getConstraint())) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
