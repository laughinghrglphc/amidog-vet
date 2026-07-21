package com.amidog.app.reservation;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ReservationEventRepository
        extends JpaRepository<ReservationEvent, Long> {
}
