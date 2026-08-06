package com.amidog.app.notification;

import com.amidog.app.auth.AccountPrincipal;
import com.amidog.app.auth.AccountType;
import com.amidog.app.reservation.Reservation;
import com.amidog.app.reservation.ReservationLifecycleDtos;
import com.amidog.app.reservation.ReservationLifecycleService;
import com.amidog.app.reservation.ReservationRepository;
import com.amidog.app.reservation.ReservationStatus;
import com.amidog.app.support.PostgresIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.TimeZone;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@Import(NotificationIntegrationTests.FixedClockConfiguration.class)
@TestPropertySource(properties = {
        "amidog.booking.auto-confirm=false",
        "amidog.booking.minimum-notice-hours=0",
        "amidog.booking.horizon-days=365",
        "amidog.notifications.reminder.enabled=true",
        "amidog.notifications.reminder.window-start-hours=23",
        "amidog.notifications.reminder.window-end-hours=25",
        "amidog.notifications.reminder.batch-size=100",
        "amidog.notifications.reminder.cron=0 0 0 1 1 *"
})
class NotificationIntegrationTests extends PostgresIntegrationTest {

    static final Instant NOW =
            Instant.parse("2026-07-29T12:00:00Z");
    private static final ZoneId CLINIC_ZONE =
            ZoneId.of("America/Santiago");

    @Autowired MockMvc mvc;
    @Autowired JdbcClient jdbc;
    @Autowired NotificationService notifications;
    @Autowired NotificationRepository notificationRepository;
    @Autowired ReservationRepository reservationRepository;
    @Autowired ReservationLifecycleService lifecycle;
    @Autowired PlatformTransactionManager transactions;

    private long administratorId;
    private TimeZone originalJvmTimeZone;

    @BeforeEach
    void cleanAndCreateAdministrator() {
        clean();
        administratorId = insertUser(
                "admin@example.com", "ADMIN", true);
        originalJvmTimeZone = TimeZone.getDefault();
    }

    @AfterEach
    void restoreJvmTimeZone() {
        TimeZone.setDefault(originalJvmTimeZone);
    }

    @Test
    void allSixBusinessTriggersUseTheCorrectRecipientAndNoTerminalNoise()
            throws Exception {
        ClientFixture ana = client("ana@example.com");
        OffsetDateTime day = OffsetDateTime.ofInstant(
                Instant.parse("2026-08-10T13:00:00Z"), CLINIC_ZONE);
        openDay(day);

        long clientCancellation = reservation(
                ana.clientId(), day, ReservationStatus.CONFIRMED);
        long clientReschedule = reservation(
                ana.clientId(), day.plusHours(1), ReservationStatus.PENDING);
        long adminConfirm = reservation(
                ana.clientId(), day.plusHours(2), ReservationStatus.PENDING);
        long adminCancellation = reservation(
                ana.clientId(), day.plusHours(3), ReservationStatus.CONFIRMED);
        long adminReschedule = reservation(
                ana.clientId(), day.plusHours(4), ReservationStatus.CONFIRMED);
        long completed = reservation(
                ana.clientId(), day.plusHours(5), ReservationStatus.CONFIRMED);
        long noShow = reservation(
                ana.clientId(), day.plusHours(6), ReservationStatus.CONFIRMED);

        mvc.perform(patch(
                        "/api/v1/me/reservations/{id}/cancel",
                        clientCancellation)
                        .with(user(ana.principal()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());
        mvc.perform(patch(
                        "/api/v1/me/reservations/{id}/reschedule",
                        clientReschedule)
                        .with(user(ana.principal()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"startsAt":"%s"}
                                """.formatted(day.plusHours(1)
                                .plusMinutes(30))))
                .andExpect(status().isOk());
        adminStatus(adminConfirm, "CONFIRMED");
        adminStatus(adminCancellation, "CANCELLED");
        mvc.perform(patch(
                        "/api/v1/admin/reservations/{id}/reschedule",
                        adminReschedule)
                        .with(user(adminPrincipal()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"startsAt":"%s"}
                                """.formatted(day.plusHours(4)
                                .plusMinutes(30))))
                .andExpect(status().isOk());
        adminStatus(completed, "COMPLETED");
        adminStatus(noShow, "NO_SHOW");

        assertThat(jdbc.sql("""
                select type from notifications
                where recipient_user_id=:admin
                order by type
                """)
                .param("admin", administratorId)
                .query(String.class).list())
                .containsExactly(
                        "CLIENT_CANCELLED",
                        "CLIENT_RESCHEDULED");
        assertThat(jdbc.sql("""
                select type from notifications
                where recipient_user_id=:client
                order by type
                """)
                .param("client", ana.userId())
                .query(String.class).list())
                .containsExactly(
                        "ADMIN_CANCELLED",
                        "ADMIN_RESCHEDULED",
                        "RESERVATION_CONFIRMED");
        assertThat(jdbc.sql("""
                select count(*) from notifications
                where reservation_id in (:completed,:noShow)
                """)
                .param("completed", completed)
                .param("noShow", noShow)
                .query(Long.class).single()).isZero();
        assertThat(jdbc.sql("""
                select count(*) from notifications
                where length(title)>120 or length(body)>500
                   or length(deduplication_key)>160
                """).query(Long.class).single()).isZero();
    }

    @Test
    void newReservationNotifiesTheSingleAdministratorWithoutPrivateContent()
            throws Exception {
        ClientFixture ana = client("ana@example.com");
        long pet = pet(ana.clientId());
        long service = service();
        OffsetDateTime start = OffsetDateTime.ofInstant(
                Instant.parse("2026-08-17T14:00:00Z"), CLINIC_ZONE);
        openDay(start);

        mvc.perform(post("/api/v1/me/reservations")
                        .with(user(ana.principal()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"startsAt":"%s",
                                 "items":[{"petId":%d,"serviceId":%d}],
                                 "note":"Contenido privado"}
                                """.formatted(start, pet, service)))
                .andExpect(status().isCreated());

        assertThat(jdbc.sql("""
                select type || ':' || recipient_user_id || ':' || body
                from notifications
                """).query(String.class).single())
                .isEqualTo(
                        "NEW_RESERVATION:" + administratorId
                                + ":Se recibi\u00f3 una nueva reserva.");
        assertThat(jdbc.sql("""
                select count(*) from notifications
                where body like '%Contenido privado%'
                """).query(Long.class).single()).isZero();
    }

    @Test
    void notificationFailureRollsBackTheReservationMutationAndEvent()
            throws Exception {
        ClientFixture ana = client("ana@example.com");
        long reservation = reservation(
                ana.clientId(),
                OffsetDateTime.ofInstant(
                        NOW.plus(Duration.ofDays(7)), CLINIC_ZONE),
                ReservationStatus.CONFIRMED);
        jdbc.sql("delete from users where id=:id")
                .param("id", administratorId).update();

        assertThatThrownBy(() -> lifecycle.cancelClient(
                ana.principal(),
                reservation,
                new ReservationLifecycleDtos.CancelReservationRequest(null)))
                .isInstanceOf(NotificationInvariantException.class);

        assertThat(jdbc.sql("""
                select status from reservations where id=:id
                """).param("id", reservation)
                .query(String.class).single())
                .isEqualTo("CONFIRMED");
        assertThat(jdbc.sql("""
                select count(*) from reservation_events
                where reservation_id=:id
                """).param("id", reservation)
                .query(Long.class).single()).isZero();
        assertThat(jdbc.sql("""
                select count(*) from notifications
                """).query(Long.class).single()).isZero();
    }

    @Test
    void exactDeduplicationIsSequentialAndConcurrentWhileForeignKeysPropagate()
            throws Exception {
        ClientFixture ana = client("ana@example.com");
        long reservationId = reservation(
                ana.clientId(),
                OffsetDateTime.ofInstant(
                        NOW.plus(Duration.ofDays(8)), CLINIC_ZONE),
                ReservationStatus.PENDING);
        Reservation reservation =
                reservationRepository.findById(reservationId).orElseThrow();

        Notification first =
                notifications.onReservationCreated(reservation);
        Notification repeated =
                notifications.onReservationCreated(reservation);
        assertThat(repeated.getId()).isEqualTo(first.getId());

        jdbc.sql("delete from notifications").update();
        var pool = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);
            Callable<Long> call = () -> {
                ready.countDown();
                start.await();
                return notifications
                        .onReservationCreated(reservation)
                        .getId();
            };
            Future<Long> left = pool.submit(call);
            Future<Long> right = pool.submit(call);
            ready.await();
            start.countDown();

            assertThat(List.of(left.get(), right.get()))
                    .containsOnly(left.get());
        } finally {
            pool.shutdownNow();
        }
        assertThat(jdbc.sql("""
                select count(*) from notifications
                """).query(Long.class).single()).isEqualTo(1L);

        assertThatThrownBy(() -> notificationRepository
                .insertIgnoringDeduplicationConflict(
                        administratorId,
                        "NEW_RESERVATION",
                        "Nueva reserva",
                        "Se recibi\u00f3 una nueva reserva.",
                        9_999_999L,
                        "unrelated-fk",
                        NOW))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void clientAndAdminApisAreRecipientScopedOrderedAndCsrfProtected()
            throws Exception {
        ClientFixture ana = client("ana@example.com");
        ClientFixture bob = client("bob@example.com");
        long anaReservation = reservation(
                ana.clientId(),
                OffsetDateTime.ofInstant(
                        NOW.plus(Duration.ofDays(9)), CLINIC_ZONE),
                ReservationStatus.PENDING);
        long bobReservation = reservation(
                bob.clientId(),
                OffsetDateTime.ofInstant(
                        NOW.plus(Duration.ofDays(10)), CLINIC_ZONE),
                ReservationStatus.PENDING);
        long older = notification(
                ana.userId(), anaReservation, "older",
                NOW.minusSeconds(20));
        long newer = notification(
                ana.userId(), anaReservation, "newer",
                NOW.minusSeconds(10));
        long foreign = notification(
                bob.userId(), bobReservation, "foreign",
                NOW.minusSeconds(5));
        long adminNotification = notification(
                administratorId, anaReservation, "admin",
                NOW);

        mvc.perform(get("/api/v1/me/notifications")
                        .with(user(ana.principal())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(newer))
                .andExpect(jsonPath("$[1].id").value(older))
                .andExpect(jsonPath("$.length()").value(2));
        mvc.perform(patch(
                        "/api/v1/me/notifications/{id}/read",
                        foreign)
                        .with(user(ana.principal()))
                        .with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        mvc.perform(patch(
                        "/api/v1/me/notifications/{id}/read",
                        newer)
                        .with(user(ana.principal())))
                .andExpect(status().isForbidden());
        mvc.perform(patch(
                        "/api/v1/me/notifications/{id}/read",
                        newer)
                        .with(user(ana.principal()))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unread").value(false));
        Instant firstRead = jdbc.sql("""
                select read_at from notifications where id=:id
                """).param("id", newer)
                .query(Instant.class).single();
        mvc.perform(patch(
                        "/api/v1/me/notifications/{id}/read",
                        newer)
                        .with(user(ana.principal()))
                        .with(csrf()))
                .andExpect(status().isOk());
        assertThat(jdbc.sql("""
                select read_at from notifications where id=:id
                """).param("id", newer)
                .query(Instant.class).single()).isEqualTo(firstRead);
        mvc.perform(post("/api/v1/me/notifications/read-all")
                        .with(user(ana.principal()))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.markedRead").value(1));
        mvc.perform(post("/api/v1/me/notifications/read-all")
                        .with(user(ana.principal()))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.markedRead").value(0));
        assertThat(jdbc.sql("""
                select count(*) from notifications
                where id in (:foreign,:admin) and read_at is null
                """)
                .param("foreign", foreign)
                .param("admin", adminNotification)
                .query(Long.class).single()).isEqualTo(2L);

        mvc.perform(get("/api/v1/admin/notifications")
                        .with(user(adminPrincipal())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(adminNotification))
                .andExpect(jsonPath("$.length()").value(1));
        mvc.perform(patch(
                        "/api/v1/admin/notifications/{id}/read",
                        adminNotification)
                        .with(user(adminPrincipal())))
                .andExpect(status().isForbidden());
        mvc.perform(patch(
                        "/api/v1/admin/notifications/{id}/read",
                        adminNotification)
                        .with(user(adminPrincipal()))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unread").value(false));
        mvc.perform(get("/api/v1/admin/notifications")
                        .with(user(ana.principal())))
                .andExpect(status().isForbidden());
        AccountPrincipal unverified = new AccountPrincipal(
                ana.userId(), ana.clientId(), ana.email(), "Ana",
                AccountType.CLIENT, "{noop}password", true, false);
        mvc.perform(get("/api/v1/me/notifications")
                        .with(user(unverified)))
                .andExpect(status().isForbidden());
    }

    @Test
    void reminderWindowUsesInstantsStatusesAndScheduledStartDeduplication() {
        ClientFixture ana = client("ana@example.com");
        Instant lower = NOW.plus(Duration.ofHours(24));
        long pending = reservation(
                ana.clientId(),
                OffsetDateTime.ofInstant(lower.plusSeconds(60), CLINIC_ZONE),
                ReservationStatus.PENDING);
        reservation(
                ana.clientId(),
                OffsetDateTime.ofInstant(lower.plusSeconds(120), CLINIC_ZONE),
                ReservationStatus.CANCELLED);
        reservation(
                ana.clientId(),
                OffsetDateTime.ofInstant(lower.plusSeconds(180), CLINIC_ZONE),
                ReservationStatus.COMPLETED);
        reservation(
                ana.clientId(),
                OffsetDateTime.ofInstant(lower.plusSeconds(240), CLINIC_ZONE),
                ReservationStatus.NO_SHOW);

        TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Auckland"));
        assertThat(notifications.createAppointmentReminders()).isZero();
        jdbc.sql("""
                update reservations
                set status='CANCELLED', updated_at=now()
                where id=:id
                """).param("id", pending).update();
        long confirmed = reservation(
                ana.clientId(),
                OffsetDateTime.ofInstant(lower, CLINIC_ZONE),
                ReservationStatus.CONFIRMED);
        assertThat(notifications.createAppointmentReminders()).isEqualTo(1);
        assertThat(notifications.createAppointmentReminders()).isZero();
        assertThat(jdbc.sql("""
                select type || ':' || recipient_user_id
                from notifications
                """).query(String.class).single())
                .isEqualTo(
                        "APPOINTMENT_REMINDER:" + ana.userId());

        jdbc.sql("""
                update reservations
                set status='CANCELLED', updated_at=now()
                where id=:id
                """).param("id", confirmed).update();
        long upper = reservation(
                ana.clientId(),
                OffsetDateTime.ofInstant(
                        NOW.plus(Duration.ofHours(25)), CLINIC_ZONE),
                ReservationStatus.CONFIRMED);
        assertThat(notifications.createAppointmentReminders()).isZero();

        jdbc.sql("""
                update reservations
                set scheduled_start=:start,
                    scheduled_end=:end,
                    updated_at=now()
                where id=:id
                """)
                .param("start", lower.plus(Duration.ofMinutes(10)))
                .param("end", lower.plus(Duration.ofMinutes(40)))
                .param("id", upper)
                .update();
        assertThat(notifications.createAppointmentReminders()).isEqualTo(1);
        assertThat(jdbc.sql("""
                select count(distinct deduplication_key)
                from notifications
                where type='APPOINTMENT_REMINDER'
                """).query(Long.class).single()).isEqualTo(2L);

        jdbc.sql("delete from notifications").update();
        var pool = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);
            Callable<Integer> scan = () -> {
                ready.countDown();
                start.await();
                return notifications.createAppointmentReminders();
            };
            Future<Integer> left = pool.submit(scan);
            Future<Integer> right = pool.submit(scan);
            ready.await();
            start.countDown();
            assertThat(left.get() + right.get()).isEqualTo(1);
        } catch (Exception exception) {
            throw new AssertionError(exception);
        } finally {
            pool.shutdownNow();
        }
        assertThat(jdbc.sql("""
                select count(*) from notifications
                where type='APPOINTMENT_REMINDER'
                """).query(Long.class).single()).isEqualTo(1L);
    }

    @Test
    void concurrentIndividualReadsKeepTheFirstCommittedTimestamp()
            throws Exception {
        ClientFixture ana = client("ana@example.com");
        long reservation = reservation(
                ana.clientId(),
                OffsetDateTime.ofInstant(
                        NOW.plus(Duration.ofDays(9)), CLINIC_ZONE),
                ReservationStatus.PENDING);
        long notification = notification(
                ana.userId(), reservation, "individual-race", NOW);
        Instant firstTimestamp = NOW.plusSeconds(10);
        Instant secondTimestamp = NOW.plusSeconds(20);

        var pool = Executors.newFixedThreadPool(2);
        CountDownLatch firstUpdated = new CountDownLatch(1);
        CountDownLatch allowFirstCommit = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);
        try {
            Future<Integer> first = pool.submit(() ->
                    inTransaction(() -> {
                        int changed =
                                notificationRepository.markOneUnread(
                                        notification,
                                        ana.userId(),
                                        firstTimestamp);
                        firstUpdated.countDown();
                        await(allowFirstCommit);
                        return changed;
                    }));
            firstUpdated.await();
            Future<Integer> second = pool.submit(() ->
                    inTransaction(() -> {
                        secondStarted.countDown();
                        return notificationRepository.markOneUnread(
                                notification,
                                ana.userId(),
                                secondTimestamp);
                    }));
            secondStarted.await();
            awaitDatabaseLock("notifications");
            allowFirstCommit.countDown();

            assertThat(first.get()).isEqualTo(1);
            assertThat(second.get()).isZero();
        } finally {
            allowFirstCommit.countDown();
            pool.shutdownNow();
        }

        assertThat(jdbc.sql("""
                select read_at from notifications where id=:id
                """).param("id", notification)
                .query(Instant.class).single())
                .isEqualTo(firstTimestamp);
    }

    @Test
    void concurrentReadAllAndIndividualReadKeepTheFirstCommittedTimestamp()
            throws Exception {
        ClientFixture ana = client("ana@example.com");
        long reservation = reservation(
                ana.clientId(),
                OffsetDateTime.ofInstant(
                        NOW.plus(Duration.ofDays(9)), CLINIC_ZONE),
                ReservationStatus.PENDING);
        long notification = notification(
                ana.userId(), reservation, "read-all-race", NOW);
        Instant firstTimestamp = NOW.plusSeconds(30);
        Instant secondTimestamp = NOW.plusSeconds(40);

        var pool = Executors.newFixedThreadPool(2);
        CountDownLatch firstUpdated = new CountDownLatch(1);
        CountDownLatch allowFirstCommit = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);
        try {
            Future<Integer> first = pool.submit(() ->
                    inTransaction(() -> {
                        int changed =
                                notificationRepository.markAllUnread(
                                        ana.userId(),
                                        firstTimestamp);
                        firstUpdated.countDown();
                        await(allowFirstCommit);
                        return changed;
                    }));
            firstUpdated.await();
            Future<Integer> second = pool.submit(() ->
                    inTransaction(() -> {
                        secondStarted.countDown();
                        return notificationRepository.markOneUnread(
                                notification,
                                ana.userId(),
                                secondTimestamp);
                    }));
            secondStarted.await();
            awaitDatabaseLock("notifications");
            allowFirstCommit.countDown();

            assertThat(first.get()).isEqualTo(1);
            assertThat(second.get()).isZero();
        } finally {
            allowFirstCommit.countDown();
            pool.shutdownNow();
        }

        assertThat(jdbc.sql("""
                select read_at from notifications where id=:id
                """).param("id", notification)
                .query(Instant.class).single())
                .isEqualTo(firstTimestamp);
    }

    @Test
    void cancellationCommittedAfterReminderSelectionPreventsTheReminder()
            throws Exception {
        ClientFixture ana = client("ana@example.com");
        long reservation = reservation(
                ana.clientId(),
                OffsetDateTime.ofInstant(
                        NOW.plus(Duration.ofHours(24)), CLINIC_ZONE),
                ReservationStatus.CONFIRMED);

        runReminderAgainstCommittedMutation(
                reservation,
                () -> jdbc.sql("""
                        update reservations
                        set status='CANCELLED', updated_at=now()
                        where id=:id
                        """).param("id", reservation).update());

        assertThat(jdbc.sql("""
                select count(*) from notifications
                where reservation_id=:id
                """).param("id", reservation)
                .query(Long.class).single()).isZero();
    }

    @Test
    void rescheduleCommittedAfterReminderSelectionPreventsTheOldReminder()
            throws Exception {
        ClientFixture ana = client("ana@example.com");
        long reservation = reservation(
                ana.clientId(),
                OffsetDateTime.ofInstant(
                        NOW.plus(Duration.ofHours(24)), CLINIC_ZONE),
                ReservationStatus.CONFIRMED);
        Instant movedStart = NOW.plus(Duration.ofDays(2));

        runReminderAgainstCommittedMutation(
                reservation,
                () -> jdbc.sql("""
                        update reservations
                        set scheduled_start=:start,
                            scheduled_end=:end,
                            updated_at=now()
                        where id=:id
                        """)
                        .param("start", movedStart)
                        .param("end", movedStart.plusSeconds(1800))
                        .param("id", reservation)
                        .update());

        assertThat(jdbc.sql("""
                select count(*) from notifications
                where reservation_id=:id
                """).param("id", reservation)
                .query(Long.class).single()).isZero();
    }

    private void adminStatus(long reservationId, String statusValue)
            throws Exception {
        mvc.perform(patch(
                        "/api/v1/admin/reservations/{id}/status",
                        reservationId)
                        .with(user(adminPrincipal()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"%s"}
                                """.formatted(statusValue)))
                .andExpect(status().isOk());
    }

    private void runReminderAgainstCommittedMutation(
            long reservationId, Runnable mutation) throws Exception {
        var pool = Executors.newFixedThreadPool(2);
        CountDownLatch rowLocked = new CountDownLatch(1);
        CountDownLatch allowMutation = new CountDownLatch(1);
        try {
            Future<Void> lifecycleMutation = pool.submit(() ->
                    inTransaction(() -> {
                        jdbc.sql("""
                                select id from reservations
                                where id=:id for update
                                """).param("id", reservationId)
                                .query(Long.class).single();
                        rowLocked.countDown();
                        await(allowMutation);
                        mutation.run();
                        return null;
                    }));
            rowLocked.await();
            Future<Integer> scan =
                    pool.submit(notifications::createAppointmentReminders);
            awaitDatabaseLock("reservations");
            assertThatThrownBy(() ->
                    scan.get(100, TimeUnit.MILLISECONDS))
                    .isInstanceOf(TimeoutException.class);
            allowMutation.countDown();

            lifecycleMutation.get();
            assertThat(scan.get()).isZero();
        } finally {
            allowMutation.countDown();
            pool.shutdownNow();
        }
    }

    private void awaitDatabaseLock(String tableName)
            throws InterruptedException {
        long deadline = System.nanoTime()
                + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            long blocked = jdbc.sql("""
                    select count(*)
                    from pg_stat_activity
                    where datname=current_database()
                      and pid <> pg_backend_pid()
                      and wait_event_type='Lock'
                      and query ilike :tablePattern
                    """)
                    .param("tablePattern", "%" + tableName + "%")
                    .query(Long.class).single();
            if (blocked > 0) {
                return;
            }
            Thread.sleep(25);
        }
        throw new AssertionError(
                "Timed out waiting for a PostgreSQL lock on "
                        + tableName);
    }

    private <T> T inTransaction(Callable<T> work) {
        return new TransactionTemplate(transactions).execute(status -> {
            try {
                return work.call();
            } catch (RuntimeException exception) {
                throw exception;
            } catch (Exception exception) {
                throw new IllegalStateException(exception);
            }
        });
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new AssertionError("Timed out waiting for test latch");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }

    private void clean() {
        jdbc.sql("delete from notifications").update();
        jdbc.sql("delete from reservation_items").update();
        jdbc.sql("delete from reservation_events").update();
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
    }

    private ClientFixture client(String email) {
        long userId = insertUser(email, "CLIENT", true);
        long clientId = jdbc.sql("""
                insert into clients(user_id,name,phone)
                values (:user,'Ana','+56911111111')
                returning id
                """).param("user", userId)
                .query(Long.class).single();
        return new ClientFixture(userId, clientId, email);
    }

    private long insertUser(
            String email, String accountType, boolean verified) {
        return jdbc.sql("""
                insert into users(
                    email_normalized,password_hash,email_verified_at,
                    account_type,enabled)
                values (:email,'{noop}password',
                        case when :verified then now() else null end,
                        :accountType,:verified)
                returning id
                """)
                .param("email", email)
                .param("accountType", accountType)
                .param("verified", verified)
                .query(Long.class).single();
    }

    private long pet(long clientId) {
        return jdbc.sql("""
                insert into pets(client_id,name,species)
                values (:client,'Milo','Gato')
                returning id
                """).param("client", clientId)
                .query(Long.class).single();
    }

    private long service() {
        return jdbc.sql("""
                insert into services(code,name)
                values ('consulta','Consulta')
                returning id
                """).query(Long.class).single();
    }

    private void openDay(OffsetDateTime start) {
        jdbc.sql("""
                insert into weekly_availability(
                    day_of_week,local_start_time,local_end_time)
                values (:day,'08:00','20:00')
                """)
                .param("day", start.getDayOfWeek().getValue())
                .update();
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

    private long notification(
            long recipientUserId,
            long reservationId,
            String key,
            Instant createdAt) {
        return jdbc.sql("""
                insert into notifications(
                    recipient_user_id,type,title,body,reservation_id,
                    deduplication_key,created_at)
                values (
                    :recipient,'RESERVATION_CONFIRMED',
                    'Reserva confirmada','Tu reserva fue confirmada.',
                    :reservation,:key,:createdAt)
                returning id
                """)
                .param("recipient", recipientUserId)
                .param("reservation", reservationId)
                .param("key", key)
                .param("createdAt", createdAt)
                .query(Long.class).single();
    }

    private AccountPrincipal adminPrincipal() {
        return new AccountPrincipal(
                administratorId, null,
                "admin@example.com", "Admin",
                AccountType.ADMIN, "{noop}password", true, true);
    }

    private record ClientFixture(
            long userId, long clientId, String email) {
        AccountPrincipal principal() {
            return new AccountPrincipal(
                    userId, clientId, email, "Ana",
                    AccountType.CLIENT, "{noop}password", true, true);
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfiguration {

        @Bean
        @Primary
        Clock fixedNotificationClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }
}
