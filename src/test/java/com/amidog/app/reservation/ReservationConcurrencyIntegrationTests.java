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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "amidog.booking.auto-confirm=false",
        "amidog.booking.minimum-notice-hours=0",
        "amidog.booking.horizon-days=365"
})
class ReservationConcurrencyIntegrationTests extends PostgresIntegrationTest {

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
    void sequentialSameSlotRequestReturnsStableConflictWithoutPartialRows()
            throws Exception {
        Fixture fixture = fixture();

        assertThat(status(fixture)).isEqualTo(201);
        var second = perform(fixture);
        assertThat(second.status()).isEqualTo(409);
        assertThat(second.body()).contains("\"code\":\"SLOT_ALREADY_BOOKED\"");
        assertAggregateCounts();
    }

    @Test
    void concurrentSameSlotRequestsProduceExactlyOneCreatedAndOneConflict()
            throws Exception {
        Fixture fixture = fixture();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try (var workers = Executors.newFixedThreadPool(2)) {
            var first = workers.submit(() -> {
                ready.countDown();
                await(start);
                return perform(fixture).status();
            });
            var second = workers.submit(() -> {
                ready.countDown();
                await(start);
                return perform(fixture).status();
            });
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            assertThat(List.of(
                    first.get(20, TimeUnit.SECONDS),
                    second.get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(201, 409);
        } finally {
            start.countDown();
        }
        assertAggregateCounts();
    }

    private void assertAggregateCounts() {
        assertThat(jdbc.sql("select count(*) from reservations")
                .query(Long.class).single()).isEqualTo(1L);
        assertThat(jdbc.sql("select count(*) from reservation_items")
                .query(Long.class).single()).isEqualTo(1L);
        assertThat(jdbc.sql("select count(*) from reservation_events")
                .query(Long.class).single()).isEqualTo(1L);
    }

    private int status(Fixture fixture) throws Exception {
        return perform(fixture).status();
    }

    private Result perform(Fixture fixture) throws Exception {
        var response = mvc.perform(post("/api/v1/me/reservations")
                        .with(user(fixture.principal())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"startsAt":"%s",
                                 "items":[{"petId":%d,"serviceId":%d}]}
                                """.formatted(
                                fixture.start(), fixture.petId(),
                                fixture.serviceId())))
                .andReturn().getResponse();
        return new Result(response.getStatus(), response.getContentAsString());
    }

    private Fixture fixture() {
        long userId = jdbc.sql("""
                insert into users(
                    email_normalized, password_hash, email_verified_at,
                    account_type, enabled)
                values ('ana@example.com','{noop}password',now(),'CLIENT',true)
                returning id
                """).query(Long.class).single();
        long clientId = jdbc.sql("""
                insert into clients(user_id,name,phone)
                values (:user,'Ana','+56911111111') returning id
                """).param("user", userId).query(Long.class).single();
        long petId = jdbc.sql("""
                insert into pets(client_id,name,species)
                values (:client,'Milo','Gato') returning id
                """).param("client", clientId).query(Long.class).single();
        long serviceId = jdbc.sql("""
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
        return new Fixture(
                new AccountPrincipal(
                        userId, clientId, "ana@example.com", "Ana",
                        AccountType.CLIENT, "{noop}password", true, true),
                petId, serviceId, start);
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Concurrency coordination timed out");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Concurrency coordination interrupted", exception);
        }
    }

    private record Fixture(
            AccountPrincipal principal,
            long petId,
            long serviceId,
            OffsetDateTime start) {
    }

    private record Result(int status, String body) {
    }
}
