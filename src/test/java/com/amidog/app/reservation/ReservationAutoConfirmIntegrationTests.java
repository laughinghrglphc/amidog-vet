package com.amidog.app.reservation;

import com.amidog.app.auth.AccountPrincipal;
import com.amidog.app.auth.AccountType;
import com.amidog.app.support.PostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "amidog.booking.auto-confirm=true",
        "amidog.booking.minimum-notice-hours=0",
        "amidog.booking.horizon-days=365"
})
class ReservationAutoConfirmIntegrationTests extends PostgresIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired JdbcClient jdbc;

    @BeforeEach
    void clean() {
        jdbc.sql("delete from reservation_items").update();
        jdbc.sql("delete from reservation_events").update();
        jdbc.sql("delete from notifications").update();
        jdbc.sql("delete from reservations").update();
        jdbc.sql("delete from availability_blocks").update();
        jdbc.sql("delete from weekly_availability").update();
        jdbc.sql("delete from pets").update();
        jdbc.sql("delete from clients").update();
        jdbc.sql("delete from email_delivery_jobs").update();
        jdbc.sql("delete from email_verification_tokens").update();
        jdbc.sql("delete from password_reset_tokens").update();
        jdbc.sql("delete from user_external_identities").update();
        jdbc.sql("delete from users").update();
        jdbc.sql("delete from services").update();
        jdbc.sql("""
                insert into users(
                    email_normalized,password_hash,email_verified_at,
                    account_type,enabled)
                values (
                    'notification-admin@example.com','{noop}password',
                    now(),'ADMIN',true)
                """).update();
    }

    @Test
    void realPropertyOverrideStartsReservationConfirmed() throws Exception {
        long user = jdbc.sql("""
                insert into users(
                    email_normalized,password_hash,email_verified_at,
                    account_type,enabled)
                values ('ana@example.com','{noop}password',now(),'CLIENT',true)
                returning id
                """).query(Long.class).single();
        long client = jdbc.sql("""
                insert into clients(user_id,name,phone)
                values (:user,'Ana','+56911111111') returning id
                """).param("user", user).query(Long.class).single();
        long pet = jdbc.sql("""
                insert into pets(client_id,name,species)
                values (:client,'Milo','Gato') returning id
                """).param("client", client).query(Long.class).single();
        long service = jdbc.sql("""
                insert into services(code,name)
                values ('consulta','Consulta') returning id
                """).query(Long.class).single();
        LocalDate date = LocalDate.now(ZoneId.of("America/Santiago")).plusDays(7);
        OffsetDateTime start = date.atTime(10, 0)
                .atZone(ZoneId.of("America/Santiago")).toOffsetDateTime();
        jdbc.sql("""
                insert into weekly_availability(
                    day_of_week,local_start_time,local_end_time)
                values (:day,'10:00','11:00')
                """).param("day", date.getDayOfWeek().getValue()).update();
        AccountPrincipal principal = new AccountPrincipal(
                user, client, "ana@example.com", "Ana",
                AccountType.CLIENT, "{noop}password", true, true);

        mvc.perform(post("/api/v1/me/reservations")
                        .with(user(principal)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"startsAt":"%s",
                                 "items":[{"petId":%d,"serviceId":%d}]}
                                """.formatted(start, pet, service)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    void clientRescheduleReappliesRealAutoConfirmProperty()
            throws Exception {
        long userId = jdbc.sql("""
                insert into users(
                    email_normalized,password_hash,email_verified_at,
                    account_type,enabled)
                values ('ana@example.com','{noop}password',now(),'CLIENT',true)
                returning id
                """).query(Long.class).single();
        long clientId = jdbc.sql("""
                insert into clients(user_id,name,phone)
                values (:user,'Ana','+56911111111') returning id
                """).param("user", userId).query(Long.class).single();
        LocalDate date = LocalDate.now(
                ZoneId.of("America/Santiago")).plusDays(7);
        OffsetDateTime start = date.atTime(10, 0)
                .atZone(ZoneId.of("America/Santiago"))
                .toOffsetDateTime();
        long reservation = jdbc.sql("""
                insert into reservations(
                    client_id,scheduled_start,scheduled_end,status)
                values (:client,:start,:end,'PENDING')
                returning id
                """)
                .param("client", clientId)
                .param("start", start.toInstant())
                .param("end", start.plusMinutes(30).toInstant())
                .query(Long.class).single();
        jdbc.sql("""
                insert into weekly_availability(
                    day_of_week,local_start_time,local_end_time)
                values (:day,'10:00','12:00')
                """).param("day", date.getDayOfWeek().getValue())
                .update();
        AccountPrincipal principal = new AccountPrincipal(
                userId, clientId, "ana@example.com", "Ana",
                AccountType.CLIENT, "{noop}password", true, true);

        mvc.perform(patch(
                        "/api/v1/me/reservations/{id}/reschedule",
                        reservation)
                        .with(user(principal)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"startsAt":"%s"}
                                """.formatted(
                                start.plusMinutes(60))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.previousStatus")
                        .value("PENDING"))
                .andExpect(jsonPath("$.status")
                        .value("CONFIRMED"));
    }
}
