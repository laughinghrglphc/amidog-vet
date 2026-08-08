package com.amidog.app.scheduling;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public class OccupiedSlotReader {

    private final JdbcClient jdbc;

    public OccupiedSlotReader(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<OccupiedSlot> findOverlapping(Instant start, Instant end) {
        return jdbc.sql("""
                select id, scheduled_start, scheduled_end
                from reservations
                where status in ('PENDING', 'CONFIRMED')
                  and scheduled_start < :end
                  and scheduled_end > :start
                order by scheduled_start, scheduled_end, id
                """)
                .param("start", start)
                .param("end", end)
                .query((row, number) -> new OccupiedSlot(
                        row.getLong("id"),
                        row.getObject("scheduled_start", java.time.OffsetDateTime.class)
                                .toInstant(),
                        row.getObject("scheduled_end", java.time.OffsetDateTime.class)
                                .toInstant()))
                .list();
    }

    public List<OccupiedSlot> findOverlappingExcluding(
            Instant start,
            Instant end,
            long excludedReservationId) {
        return jdbc.sql("""
                select id, scheduled_start, scheduled_end
                from reservations
                where status in ('PENDING', 'CONFIRMED')
                  and id <> :excludedReservationId
                  and scheduled_start < :end
                  and scheduled_end > :start
                order by scheduled_start, scheduled_end, id
                """)
                .param("excludedReservationId", excludedReservationId)
                .param("start", start)
                .param("end", end)
                .query((row, number) -> new OccupiedSlot(
                        row.getLong("id"),
                        row.getObject(
                                        "scheduled_start",
                                        java.time.OffsetDateTime.class)
                                .toInstant(),
                        row.getObject(
                                        "scheduled_end",
                                        java.time.OffsetDateTime.class)
                                .toInstant()))
                .list();
    }

    public record OccupiedSlot(long reservationId, Instant start, Instant end) {
    }
}
