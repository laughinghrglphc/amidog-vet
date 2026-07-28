package com.amidog.app.client;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class PetReservationGuard {

    private final JdbcClient jdbc;

    public PetReservationGuard(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public boolean hasFutureOccupied(long petId, Instant now) {
        return jdbc.sql("""
                select exists(
                  select 1 from reservation_items ri
                  join reservations r on r.id = ri.reservation_id
                  where ri.pet_id = :petId
                    and r.status in ('PENDING', 'CONFIRMED')
                    and r.scheduled_start > :now
                )
                """)
                .param("petId", petId)
                .param("now", now)
                .query(Boolean.class)
                .single();
    }
}
