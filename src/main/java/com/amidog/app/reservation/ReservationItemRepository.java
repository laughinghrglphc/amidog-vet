package com.amidog.app.reservation;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ReservationItemRepository
        extends JpaRepository<ReservationItem, Long> {
}
