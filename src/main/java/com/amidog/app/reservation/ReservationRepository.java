package com.amidog.app.reservation;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ReservationRepository
        extends JpaRepository<Reservation, Long> {

    @EntityGraph(attributePaths = {"items", "items.pet", "items.service"})
    List<Reservation> findAllByClientIdOrderByScheduledStartDescIdDesc(
            Long clientId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select reservation
            from Reservation reservation
            where reservation.id = :id
              and reservation.client.id = :clientId
            """)
    Optional<Reservation> findOwnedForUpdate(
            @Param("id") Long id,
            @Param("clientId") Long clientId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select reservation
            from Reservation reservation
            where reservation.id = :id
            """)
    Optional<Reservation> findByIdForUpdate(@Param("id") Long id);

    @Query("""
            select reservation.id as reservationId,
                   reservation.scheduledStart as scheduledStart,
                   reservation.client.user.id as recipientUserId
            from Reservation reservation
            where reservation.status =
                  com.amidog.app.reservation.ReservationStatus.CONFIRMED
              and reservation.scheduledStart >= :from
              and reservation.scheduledStart < :to
            order by reservation.scheduledStart asc, reservation.id asc
            """)
    List<ReminderCandidate> findConfirmedReminderCandidates(
            @Param("from") Instant from,
            @Param("to") Instant to,
            Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"client", "client.user"})
    @Query("""
            select reservation
            from Reservation reservation
            where reservation.id = :id
            """)
    Optional<Reservation> findReminderForUpdate(
            @Param("id") Long id);

    interface ReminderCandidate {
        Long getReservationId();

        Instant getScheduledStart();

        Long getRecipientUserId();
    }
}
