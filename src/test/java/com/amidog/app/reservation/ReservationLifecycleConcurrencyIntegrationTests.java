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
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;

@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "amidog.booking.auto-confirm=false",
        "amidog.booking.minimum-notice-hours=0",
        "amidog.booking.horizon-days=365"
})
class ReservationLifecycleConcurrencyIntegrationTests
        extends PostgresIntegrationTest {

    private static final ZoneId CLINIC_ZONE =
            ZoneId.of("America/Santiago");

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
    void concurrentReschedulesToOneSlotCommitOneAggregateAndOneEvent()
            throws Exception {
        ClientFixture ana = client("ana@example.com");
        ClientFixture bob = client("bob@example.com");
        LocalDate date = LocalDate.now(CLINIC_ZONE).plusDays(7);
        open(date);
        OffsetDateTime first = at(date, 9, 0);
        OffsetDateTime second = at(date, 10, 0);
        OffsetDateTime target = at(date, 11, 0);
        long anaReservation = reservation(
                ana.clientId(), first, ReservationStatus.PENDING);
        long bobReservation = reservation(
                bob.clientId(), second, ReservationStatus.PENDING);

        List<Integer> statuses = runTogether(
                () -> reschedule(
                        ana, anaReservation, target),
                () -> reschedule(
                        bob, bobReservation, target));

        assertThat(statuses).containsExactlyInAnyOrder(200, 409);
        assertThat(jdbc.sql("""
                select count(*) from reservations
                where scheduled_start=:target
                  and status in ('PENDING','CONFIRMED')
                """).param("target", target.toInstant())
                .query(Long.class).single()).isEqualTo(1L);
        assertThat(jdbc.sql("""
                select count(*) from reservation_events
                where event_type='RESCHEDULED'
                """).query(Long.class).single()).isEqualTo(1L);
        assertThat(jdbc.sql("""
                select count(*) from reservations
                where scheduled_start in (:first,:second)
                """).param("first", first.toInstant())
                .param("second", second.toInstant())
                .query(Long.class).single()).isEqualTo(1L);
    }

    @Test
    void simultaneousTerminalStatusChangesCannotOverwriteOrLoseAnEvent()
            throws Exception {
        ClientFixture ana = client("ana@example.com");
        long reservation = reservation(
                ana.clientId(),
                OffsetDateTime.now(CLINIC_ZONE).plusDays(7),
                ReservationStatus.CONFIRMED);

        List<Integer> statuses = runTogether(
                () -> adminStatus(
                        reservation,
                        ReservationStatus.COMPLETED),
                () -> adminStatus(
                        reservation,
                        ReservationStatus.NO_SHOW));

        assertThat(statuses).containsExactlyInAnyOrder(200, 409);
        assertThat(jdbc.sql("""
                select status from reservations where id=:id
                """).param("id", reservation)
                .query(String.class).single())
                .isIn("COMPLETED", "NO_SHOW");
        assertThat(jdbc.sql("""
                select count(*) from reservation_events
                where reservation_id=:id
                """).param("id", reservation)
                .query(Long.class).single()).isEqualTo(1L);
        assertThat(jdbc.sql("""
                select new_status from reservation_events
                where reservation_id=:id
                """).param("id", reservation)
                .query(String.class).single())
                .isIn("COMPLETED", "NO_SHOW");
    }

    private List<Integer> runTogether(
            Request first,
            Request second) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var workers = Executors.newFixedThreadPool(2)) {
            var firstResult = workers.submit(() -> {
                ready.countDown();
                await(start);
                return first.perform();
            });
            var secondResult = workers.submit(() -> {
                ready.countDown();
                await(start);
                return second.perform();
            });
            assertThat(ready.await(10, TimeUnit.SECONDS))
                    .isTrue();
            start.countDown();
            return List.of(
                    firstResult.get(20, TimeUnit.SECONDS),
                    secondResult.get(20, TimeUnit.SECONDS));
        } finally {
            start.countDown();
        }
    }

    private int reschedule(
            ClientFixture client,
            long reservation,
            OffsetDateTime target) throws Exception {
        return mvc.perform(patch(
                        "/api/v1/me/reservations/{id}/reschedule",
                        reservation)
                        .with(user(client.principal()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"startsAt":"%s"}
                                """.formatted(target)))
                .andReturn().getResponse().getStatus();
    }

    private int adminStatus(
            long reservation,
            ReservationStatus status) throws Exception {
        return mvc.perform(patch(
                        "/api/v1/admin/reservations/{id}/status",
                        reservation)
                        .with(user(admin()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"%s"}
                                """.formatted(status)))
                .andReturn().getResponse().getStatus();
    }

    private void open(LocalDate date) {
        jdbc.sql("""
                insert into weekly_availability(
                    day_of_week,local_start_time,local_end_time)
                values (:day,'09:00','13:00')
                """).param("day", date.getDayOfWeek().getValue())
                .update();
    }

    private static OffsetDateTime at(
            LocalDate date,
            int hour,
            int minute) {
        return date.atTime(hour, minute)
                .atZone(CLINIC_ZONE).toOffsetDateTime();
    }

    private ClientFixture client(String email) {
        long userId = jdbc.sql("""
                insert into users(
                    email_normalized,password_hash,email_verified_at,
                    account_type,enabled)
                values (:email,'{noop}password',now(),'CLIENT',true)
                returning id
                """).param("email", email)
                .query(Long.class).single();
        long clientId = jdbc.sql("""
                insert into clients(user_id,name,phone)
                values (:user,'Ana','+56911111111') returning id
                """).param("user", userId)
                .query(Long.class).single();
        return new ClientFixture(userId, clientId, email);
    }

    private long reservation(
            long clientId,
            OffsetDateTime start,
            ReservationStatus status) {
        return jdbc.sql("""
                insert into reservations(
                    client_id,scheduled_start,scheduled_end,status)
                values (:client,:start,:end,:status)
                returning id
                """)
                .param("client", clientId)
                .param("start", start.toInstant())
                .param("end", start.plusMinutes(30).toInstant())
                .param("status", status.name())
                .query(Long.class).single();
    }

    private static AccountPrincipal admin() {
        return new AccountPrincipal(
                999L, null, "admin@example.com", "Admin",
                AccountType.ADMIN, "{noop}password", true, true);
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException(
                        "Concurrency coordination timed out");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                    "Concurrency coordination interrupted",
                    exception);
        }
    }

    private record ClientFixture(
            long userId,
            long clientId,
            String email) {
        AccountPrincipal principal() {
            return new AccountPrincipal(
                    userId, clientId, email, "Ana",
                    AccountType.CLIENT, "{noop}password",
                    true, true);
        }
    }

    @FunctionalInterface
    private interface Request {
        int perform() throws Exception;
    }
}
