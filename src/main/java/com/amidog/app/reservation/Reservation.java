package com.amidog.app.reservation;

import com.amidog.app.catalog.ServiceOffering;
import com.amidog.app.client.Client;
import com.amidog.app.client.Pet;
import com.amidog.app.common.api.ConflictException;
import com.amidog.app.common.api.ConflictType;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Entity
@Table(name = "reservations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Reservation {

    private static final Duration DURATION = Duration.ofMinutes(30);
    private static final int REASON_MAXIMUM = 300;
    private static final Map<ReservationStatus, Set<ReservationStatus>>
            ALLOWED_TRANSITIONS = Map.of(
                    ReservationStatus.PENDING,
                    Set.of(
                            ReservationStatus.CONFIRMED,
                            ReservationStatus.CANCELLED),
                    ReservationStatus.CONFIRMED,
                    Set.of(
                            ReservationStatus.CANCELLED,
                            ReservationStatus.COMPLETED,
                            ReservationStatus.NO_SHOW),
                    ReservationStatus.CANCELLED, Set.of(),
                    ReservationStatus.COMPLETED, Set.of(),
                    ReservationStatus.NO_SHOW, Set.of());

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "client_id", nullable = false)
    private Client client;

    @Column(name = "scheduled_start", nullable = false)
    private Instant scheduledStart;

    @Column(name = "scheduled_end", nullable = false)
    private Instant scheduledEnd;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReservationStatus status;

    @Column(name = "client_note", length = 500)
    private String clientNote;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancelled_by", length = 20)
    private String cancelledBy;

    @Column(name = "cancellation_reason", length = 300)
    private String cancellationReason;

    @Version
    private long version;

    @OneToMany(
            mappedBy = "reservation",
            cascade = CascadeType.ALL,
            orphanRemoval = true)
    @OrderBy("id ASC")
    private List<ReservationItem> items = new ArrayList<>();

    @OneToMany(
            mappedBy = "reservation",
            cascade = CascadeType.ALL,
            orphanRemoval = true)
    @OrderBy("createdAt ASC, id ASC")
    private List<ReservationEvent> events = new ArrayList<>();

    public static Reservation create(
            Client client,
            Instant scheduledStart,
            ReservationStatus status,
            String clientNote,
            Instant now) {
        return new Reservation(
                client, scheduledStart, status, clientNote, now);
    }

    private Reservation(
            Client client,
            Instant scheduledStart,
            ReservationStatus status,
            String clientNote,
            Instant now) {
        this.client = Objects.requireNonNull(client, "client");
        this.scheduledStart =
                Objects.requireNonNull(scheduledStart, "scheduledStart");
        this.scheduledEnd = scheduledStart.plus(DURATION);
        this.status = Objects.requireNonNull(status, "status");
        this.clientNote = trimToNull(clientNote);
        if (this.clientNote != null && this.clientNote.length() > 500) {
            throw new IllegalArgumentException(
                    "clientNote must not exceed 500 characters");
        }
        this.createdAt = Objects.requireNonNull(now, "now");
        this.updatedAt = now;
    }

    public void addItem(Pet pet, ServiceOffering service) {
        items.add(ReservationItem.create(this, pet, service));
    }

    public void addCreationEvent(Instant now) {
        events.add(ReservationEvent.created(this, now));
    }

    public void transitionTo(
            ReservationStatus target,
            ReservationActor actor,
            String reason,
            Instant now) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(now, "now");
        if (!ALLOWED_TRANSITIONS
                .getOrDefault(status, Set.of())
                .contains(target)) {
            throw new ConflictException(
                    ConflictType.INVALID_RESERVATION_STATUS_TRANSITION);
        }
        String normalizedReason = normalizeReason(reason);
        ReservationStatus previousStatus = status;
        if (target == ReservationStatus.CANCELLED) {
            cancelledAt = now;
            cancelledBy = actor.name();
            cancellationReason = normalizedReason;
        }
        status = target;
        updatedAt = now;
        events.add(ReservationEvent.statusChanged(
                this,
                previousStatus,
                target,
                actor,
                normalizedReason,
                now));
    }

    public void reschedule(
            Instant newStart,
            ReservationStatus newStatus,
            ReservationActor actor,
            Instant now) {
        Objects.requireNonNull(newStart, "newStart");
        Objects.requireNonNull(newStatus, "newStatus");
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(now, "now");
        if ((status != ReservationStatus.PENDING
                && status != ReservationStatus.CONFIRMED)
                || (newStatus != ReservationStatus.PENDING
                && newStatus != ReservationStatus.CONFIRMED)) {
            throw new ConflictException(
                    ConflictType.INVALID_RESERVATION_STATUS_TRANSITION);
        }
        if (scheduledStart.equals(newStart)) {
            throw new ConflictException(
                    ConflictType.RESERVATION_START_UNCHANGED);
        }

        ReservationStatus previousStatus = status;
        Instant previousStart = scheduledStart;
        scheduledStart = newStart;
        scheduledEnd = newStart.plus(DURATION);
        status = newStatus;
        updatedAt = now;
        cancelledAt = null;
        cancelledBy = null;
        cancellationReason = null;
        events.add(ReservationEvent.rescheduled(
                this,
                previousStatus,
                newStatus,
                previousStart,
                newStart,
                actor,
                now));
    }

    public List<ReservationItem> getItems() {
        return Collections.unmodifiableList(items);
    }

    public List<ReservationEvent> getEvents() {
        return Collections.unmodifiableList(events);
    }

    private static String trimToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }

    private static String normalizeReason(String value) {
        String normalized = trimToNull(value);
        if (normalized != null && normalized.length() > REASON_MAXIMUM) {
            throw new IllegalArgumentException(
                    "reason must not exceed 300 characters");
        }
        return normalized;
    }
}
