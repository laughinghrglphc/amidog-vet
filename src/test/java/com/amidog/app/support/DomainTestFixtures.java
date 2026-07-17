package com.amidog.app.support;

import org.springframework.jdbc.core.simple.JdbcClient;

import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;

public final class DomainTestFixtures {

    private final JdbcClient jdbc;

    public DomainTestFixtures(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public long activeService(String name) {
        return service("test-" + UUID.randomUUID(), name);
    }

    public long service(String code, String name) {
        return jdbc.sql("""
                insert into services(code, name)
                values (:code, :name)
                returning id
                """)
                .param("code", code)
                .param("name", name)
                .query(Long.class)
                .single();
    }

    public long pet(long clientId, String name, String species) {
        return jdbc.sql("""
                insert into pets(client_id, name, species)
                values (:clientId, :name, :species)
                returning id
                """)
                .param("clientId", clientId)
                .param("name", name)
                .param("species", species)
                .query(Long.class)
                .single();
    }

    public long weeklyHours(int dayOfWeek, LocalTime start, LocalTime end) {
        return jdbc.sql("""
                insert into weekly_availability(
                    day_of_week, local_start_time, local_end_time)
                values (:dayOfWeek, :start, :end)
                returning id
                """)
                .param("dayOfWeek", dayOfWeek)
                .param("start", start)
                .param("end", end)
                .query(Long.class)
                .single();
    }

    public long block(Instant start, Instant end, String reason) {
        return jdbc.sql("""
                insert into availability_blocks(start_at, end_at, reason)
                values (:start, :end, :reason)
                returning id
                """)
                .param("start", start)
                .param("end", end)
                .param("reason", reason)
                .query(Long.class)
                .single();
    }

    public long reservation(long clientId, Instant start, String status) {
        return jdbc.sql("""
                insert into reservations(
                    client_id, scheduled_start, scheduled_end, status)
                values (:clientId, :start, :end, :status)
                returning id
                """)
                .param("clientId", clientId)
                .param("start", start)
                .param("end", start.plusSeconds(30 * 60))
                .param("status", status)
                .query(Long.class)
                .single();
    }

    public long reservationItem(
            long reservationId,
            long petId,
            long serviceId,
            String serviceNameSnapshot) {
        return jdbc.sql("""
                insert into reservation_items(
                    reservation_id, pet_id, service_id, service_name_snapshot)
                values (:reservationId, :petId, :serviceId, :snapshot)
                returning id
                """)
                .param("reservationId", reservationId)
                .param("petId", petId)
                .param("serviceId", serviceId)
                .param("snapshot", serviceNameSnapshot)
                .query(Long.class)
                .single();
    }
}
