package com.amidog.app.scheduling;

import com.amidog.app.auth.AccountPrincipal;
import com.amidog.app.auth.AccountType;
import com.amidog.app.support.PostgresIntegrationTest;
import com.amidog.app.support.TestRows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "amidog.booking.minimum-notice-hours=0",
        "amidog.booking.horizon-days=90"
})
class AvailabilityIntegrationTests extends PostgresIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired JdbcClient jdbc;
    @Autowired SchedulingMutationLock schedulingMutationLock;
    @Autowired PlatformTransactionManager transactionManager;

    @BeforeEach
    void clean() {
        jdbc.sql("delete from reservation_items").update();
        jdbc.sql("delete from reservation_events").update();
        jdbc.sql("delete from notifications").update();
        jdbc.sql("delete from reservations").update();
        jdbc.sql("delete from availability_blocks").update();
        jdbc.sql("delete from weekly_availability").update();
    }

    @Test
    void publicAvailabilityReturnsSplitShiftsAsDtosAndRejectsReverseRange() throws Exception {
        LocalDate monday = nextWeekday(1);
        insertWeekly(1, "09:00", "10:00", true);
        insertWeekly(1, "15:00", "16:00", true);

        mvc.perform(get("/api/v1/availability")
                        .param("from", monday.toString()).param("to", monday.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[0].startsAt").exists())
                .andExpect(jsonPath("$[0].id").doesNotExist());

        mvc.perform(get("/api/v1/availability")
                        .param("from", monday.plusDays(1).toString())
                        .param("to", monday.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_DATE_RANGE"));
    }

    @Test
    void adminWeeklyReplaceValidatesRoleCsrfOverlapAndEmptyClosure() throws Exception {
        String overlap = """
                [
                  {"dayOfWeek":1,"start":"09:00","end":"11:00","active":true},
                  {"dayOfWeek":1,"start":"10:30","end":"12:00","active":true}
                ]
                """;
        mvc.perform(put("/api/v1/admin/availability/weekly")
                        .with(user(admin())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(overlap))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("WEEKLY_INTERVAL_OVERLAP"));

        mvc.perform(put("/api/v1/admin/availability/weekly")
                        .with(user(client())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("[]"))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/admin/availability/weekly")
                        .with(user(admin()))
                        .contentType(MediaType.APPLICATION_JSON).content("[]"))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/admin/availability/weekly")
                        .with(user(admin())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("[]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void partialBlockUsesHalfOpenOverlapAndCanBeListedAndDeleted() throws Exception {
        LocalDate monday = nextWeekday(1);
        insertWeekly(1, "09:00", "11:00", true);
        OffsetDateTime nineThirty = monday.atTime(9, 30)
                .atZone(java.time.ZoneId.of("America/Santiago")).toOffsetDateTime();
        OffsetDateTime ten = monday.atTime(10, 0)
                .atZone(java.time.ZoneId.of("America/Santiago")).toOffsetDateTime();

        String response = mvc.perform(post("/api/v1/admin/availability/blocks")
                        .with(user(admin())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"startsAt":"%s","endsAt":"%s","reason":" Cirug\u00eda "}
                                """.formatted(nineThirty, ten)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reason").value("Cirug\u00eda"))
                .andReturn().getResponse().getContentAsString();
        long id = Long.parseLong(response.replaceAll("(?s).*\"id\":(\\d+).*", "$1"));

        mvc.perform(get("/api/v1/availability")
                        .param("from", monday.toString()).param("to", monday.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3));
        mvc.perform(get("/api/v1/admin/availability/blocks").with(user(admin())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(id));
        mvc.perform(delete("/api/v1/admin/availability/blocks/{id}", id)
                        .with(user(admin())).with(csrf()))
                .andExpect(status().isNoContent());
        mvc.perform(delete("/api/v1/admin/availability/blocks/{id}", id)
                        .with(user(admin())).with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    void blockConflictIncludesOnlyPendingAndConfirmedReservationIds() throws Exception {
        long clientId = TestRows.verifiedClient(jdbc);
        LocalDate monday = nextWeekday(1);
        OffsetDateTime start = monday.atTime(10, 0)
                .atZone(java.time.ZoneId.of("America/Santiago")).toOffsetDateTime();
        long pending = reservation(clientId, start, "PENDING");
        long confirmed = reservation(clientId, start.plusMinutes(30), "CONFIRMED");
        reservation(clientId, start.plusMinutes(60), "CANCELLED");

        mvc.perform(post("/api/v1/admin/availability/blocks")
                        .with(user(admin())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"startsAt":"%s","endsAt":"%s"}
                                """.formatted(start, start.plusMinutes(90))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BLOCK_OVERLAPS_RESERVATIONS"))
                .andExpect(jsonPath("$.details.reservationIds[0]").value(pending))
                .andExpect(jsonPath("$.details.reservationIds[1]").value(confirmed));
        assertThat(jdbc.sql("select count(*) from availability_blocks")
                .query(Long.class).single()).isZero();
    }

    @Test
    void sharedTransactionLockSerializesBlockAndFutureReservationWriters()
            throws Exception {
        CountDownLatch blockWriterHasLock = new CountDownLatch(1);
        CountDownLatch releaseBlockWriter = new CountDownLatch(1);
        CountDownLatch reservationWriterStarted = new CountDownLatch(1);
        TransactionTemplate transactions = new TransactionTemplate(transactionManager);

        try (var workers = Executors.newFixedThreadPool(2)) {
            var blockWriter = workers.submit(() ->
                    transactions.executeWithoutResult(status -> {
                        schedulingMutationLock.acquire();
                        blockWriterHasLock.countDown();
                        await(releaseBlockWriter);
                    }));
            assertThat(blockWriterHasLock.await(10, TimeUnit.SECONDS)).isTrue();

            var simulatedReservationWriter = workers.submit(() ->
                    transactions.executeWithoutResult(status -> {
                        reservationWriterStarted.countDown();
                        schedulingMutationLock.acquire();
                    }));
            assertThat(reservationWriterStarted.await(10, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() ->
                    simulatedReservationWriter.get(250, TimeUnit.MILLISECONDS))
                    .isInstanceOf(java.util.concurrent.TimeoutException.class);

            releaseBlockWriter.countDown();
            blockWriter.get(10, TimeUnit.SECONDS);
            simulatedReservationWriter.get(10, TimeUnit.SECONDS);
        } finally {
            releaseBlockWriter.countDown();
        }
    }

    private void insertWeekly(int day, String start, String end, boolean active) {
        jdbc.sql("""
                insert into weekly_availability(
                    day_of_week, local_start_time, local_end_time, active)
                values (:day, cast(:start as time), cast(:end as time), :active)
                """)
                .param("day", day).param("start", start).param("end", end)
                .param("active", active).update();
    }

    private long reservation(long clientId, OffsetDateTime start, String status) {
        return jdbc.sql("""
                insert into reservations(client_id, scheduled_start, scheduled_end, status)
                values (:client, :start, :end, :status)
                returning id
                """)
                .param("client", clientId)
                .param("start", start.toInstant())
                .param("end", start.plusMinutes(30).toInstant())
                .param("status", status)
                .query(Long.class).single();
    }

    private static LocalDate nextWeekday(int isoDay) {
        LocalDate date = LocalDate.now(java.time.ZoneId.of("America/Santiago")).plusDays(2);
        while (date.getDayOfWeek().getValue() != isoDay) {
            date = date.plusDays(1);
        }
        return date;
    }

    private static AccountPrincipal admin() {
        return new AccountPrincipal(90L, null, "admin@example.com", "Admin",
                AccountType.ADMIN, "{noop}password", true, true);
    }

    private static AccountPrincipal client() {
        return new AccountPrincipal(91L, 91L, "client@example.com", "Cliente",
                AccountType.CLIENT, "{noop}password", true, true);
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Scheduling lock test coordination timed out");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Scheduling lock test interrupted", exception);
        }
    }
}
