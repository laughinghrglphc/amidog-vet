package com.amidog.app.reservation;

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
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "reservation_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReservationEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reservation_id", nullable = false)
    private Reservation reservation;

    @Column(name = "event_type", nullable = false, length = 30)
    private String eventType;

    @Column(name = "actor_type", nullable = false, length = 20)
    private String actorType;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status", length = 20)
    private ReservationStatus previousStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_status", length = 20)
    private ReservationStatus newStatus;

    @Column(name = "previous_start")
    private Instant previousStart;

    @Column(name = "new_start")
    private Instant newStart;

    @Column(length = 300)
    private String reason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    static ReservationEvent created(
            Reservation reservation, Instant now) {
        return new ReservationEvent(reservation, now);
    }

    static ReservationEvent statusChanged(
            Reservation reservation,
            ReservationStatus previousStatus,
            ReservationStatus newStatus,
            ReservationActor actor,
            String reason,
            Instant now) {
        return new ReservationEvent(
                reservation,
                newStatus == ReservationStatus.CANCELLED
                        ? ReservationEventType.CANCELLED
                        : ReservationEventType.STATUS_CHANGED,
                actor,
                previousStatus,
                newStatus,
                null,
                null,
                reason,
                now);
    }

    static ReservationEvent rescheduled(
            Reservation reservation,
            ReservationStatus previousStatus,
            ReservationStatus newStatus,
            Instant previousStart,
            Instant newStart,
            ReservationActor actor,
            Instant now) {
        return new ReservationEvent(
                reservation,
                ReservationEventType.RESCHEDULED,
                actor,
                previousStatus,
                newStatus,
                previousStart,
                newStart,
                null,
                now);
    }

    private ReservationEvent(Reservation reservation, Instant now) {
        this.reservation =
                Objects.requireNonNull(reservation, "reservation");
        this.eventType = ReservationEventType.CREATED.name();
        this.actorType = ReservationActor.CLIENT.name();
        this.newStatus = reservation.getStatus();
        this.newStart = reservation.getScheduledStart();
        this.createdAt = Objects.requireNonNull(now, "now");
    }

    private ReservationEvent(
            Reservation reservation,
            ReservationEventType eventType,
            ReservationActor actor,
            ReservationStatus previousStatus,
            ReservationStatus newStatus,
            Instant previousStart,
            Instant newStart,
            String reason,
            Instant now) {
        this.reservation =
                Objects.requireNonNull(reservation, "reservation");
        this.eventType =
                Objects.requireNonNull(eventType, "eventType").name();
        this.actorType = Objects.requireNonNull(actor, "actor").name();
        this.previousStatus = previousStatus;
        this.newStatus = newStatus;
        this.previousStart = previousStart;
        this.newStart = newStart;
        this.reason = reason;
        this.createdAt = Objects.requireNonNull(now, "now");
    }
}
