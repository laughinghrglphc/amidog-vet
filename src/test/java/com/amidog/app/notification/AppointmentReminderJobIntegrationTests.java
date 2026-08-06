package com.amidog.app.notification;

import com.amidog.app.auth.UserAccount;
import com.amidog.app.auth.UserAccountRepository;
import com.amidog.app.client.Client;
import com.amidog.app.client.ClientRepository;
import com.amidog.app.client.CurrentClient;
import com.amidog.app.reservation.Reservation;
import com.amidog.app.reservation.ReservationRepository;
import com.amidog.app.reservation.ReservationStatus;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Deterministic job/service contract tests. PostgreSQL-specific query and
 * conflict behavior remains covered by NotificationIntegrationTests.
 */
class AppointmentReminderJobIntegrationTests {

    private static final Instant NOW =
            Instant.parse("2026-07-30T12:00:00Z");

    @Test
    void usesInclusiveTwentyThreeHourAndExclusiveTwentyFiveHourBounds()
            throws Exception {
        Harness harness = new Harness();
        Reservation lower = harness.add(
                1L, ReservationStatus.CONFIRMED,
                NOW.plus(Duration.ofHours(23)));
        Reservation beforeUpper = harness.add(
                2L, ReservationStatus.CONFIRMED,
                NOW.plus(Duration.ofHours(25)).minusNanos(1));
        harness.add(
                3L, ReservationStatus.CONFIRMED,
                NOW.plus(Duration.ofHours(23)).minusNanos(1));
        harness.add(
                4L, ReservationStatus.CONFIRMED,
                NOW.plus(Duration.ofHours(25)));

        assertThat(harness.service.createAppointmentReminders())
                .isEqualTo(2);
        assertThat(harness.from.get())
                .isEqualTo(NOW.plus(Duration.ofHours(23)));
        assertThat(harness.to.get())
                .isEqualTo(NOW.plus(Duration.ofHours(25)));
        assertThat(harness.reminderReservationIds())
                .containsExactlyInAnyOrder(lower.getId(), beforeUpper.getId());
    }

    @Test
    void createsOneReservationBoundReminderWhenTheHourlyScanIsRetried()
            throws Exception {
        Harness harness = new Harness();
        Reservation reservation = harness.add(
                20L, ReservationStatus.CONFIRMED,
                NOW.plus(Duration.ofHours(24)));

        int first = harness.service.createAppointmentReminders();
        int repeated = harness.service.createAppointmentReminders();

        assertThat(first).isEqualTo(1);
        assertThat(repeated).isZero();
        assertThat(harness.reminders()).singleElement()
                .satisfies(notification -> {
                    assertThat(notification.getReservation())
                            .isSameAs(reservation);
                    assertThat(notification.getDeduplicationKey())
                            .contains(
                                    "APPOINTMENT_REMINDER",
                                    "r:" + reservation.getId());
                });
    }

    @Test
    void ignoresEveryNonConfirmedReservationStatus() throws Exception {
        Harness harness = new Harness();
        harness.add(
                30L, ReservationStatus.PENDING,
                NOW.plus(Duration.ofHours(24)));
        harness.add(
                31L, ReservationStatus.CANCELLED,
                NOW.plus(Duration.ofHours(24)));
        harness.add(
                32L, ReservationStatus.COMPLETED,
                NOW.plus(Duration.ofHours(24)));
        harness.add(
                33L, ReservationStatus.NO_SHOW,
                NOW.plus(Duration.ofHours(24)));

        assertThat(harness.service.createAppointmentReminders()).isZero();
        assertThat(harness.reminders()).isEmpty();
    }

    @Test
    void schedulerUsesTheHourlyCronAndDelegatesToTheCallableService()
            throws Exception {
        AtomicInteger calls = new AtomicInteger();
        NotificationService service = new NotificationService(
                null, null, null, null, Clock.fixed(NOW, ZoneOffset.UTC),
                new NotificationReminderProperties(true, 23, 25, 100)) {
            @Override
            public int createAppointmentReminders() {
                calls.incrementAndGet();
                return 0;
            }
        };
        AppointmentReminderScheduler scheduler =
                new AppointmentReminderScheduler(service);

        scheduler.scan();

        Scheduled schedule = AppointmentReminderScheduler.class
                .getMethod("scan")
                .getAnnotation(Scheduled.class);
        assertThat(calls).hasValue(1);
        assertThat(schedule.cron()).isEqualTo(
                "${amidog.notifications.reminder.cron:0 5 * * * *}");
        assertThat(schedule.zone()).isEqualTo(
                "${amidog.clinic-zone:America/Santiago}");
    }

    @Test
    void rejectsAConfiguredWindowWhoseEndIsNotAfterItsStart() {
        assertThatThrownBy(() ->
                new NotificationReminderProperties(
                        true, 25, 23, 100))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("reminder window end must be after its start");
    }

    private static final class Harness {

        private final AtomicReference<Instant> from =
                new AtomicReference<>();
        private final AtomicReference<Instant> to =
                new AtomicReference<>();
        private final List<ReservationRepository.ReminderCandidate>
                candidates = new ArrayList<>();
        private final Map<Long, Reservation> reservationsById =
                new HashMap<>();
        private final Map<String, Notification> notificationsByKey =
                new HashMap<>();
        private final NotificationService service;
        private UserAccount clientUser;

        private Harness() throws Exception {
            NotificationRepository notifications = proxy(
                    NotificationRepository.class,
                    (method, arguments) -> switch (method.getName()) {
                        case "insertIgnoringDeduplicationConflict" ->
                                insertNotification(arguments);
                        case "findByDeduplicationKey" ->
                                Optional.ofNullable(
                                        notificationsByKey.get(arguments[0]));
                        default -> throw unsupported(method.getName());
                    });
            ReservationRepository reservations = proxy(
                    ReservationRepository.class,
                    (method, arguments) -> switch (method.getName()) {
                        case "findConfirmedReminderCandidates" -> {
                            from.set((Instant) arguments[0]);
                            to.set((Instant) arguments[1]);
                            assertThat(((Pageable) arguments[2])
                                    .getPageSize()).isEqualTo(100);
                            yield List.copyOf(candidates);
                        }
                        case "findReminderForUpdate" ->
                                Optional.ofNullable(
                                        reservationsById.get(arguments[0]));
                        default -> throw unsupported(method.getName());
                    });
            UserAccountRepository users = proxy(
                    UserAccountRepository.class,
                    (method, arguments) -> {
                        throw unsupported(method.getName());
                    });
            ClientRepository clients = proxy(
                    ClientRepository.class,
                    (method, arguments) -> {
                        throw unsupported(method.getName());
                    });
            service = new NotificationService(
                    notifications,
                    users,
                    reservations,
                    new CurrentClient(clients),
                    Clock.fixed(NOW, ZoneOffset.UTC),
                    new NotificationReminderProperties(
                            true, 23, 25, 100));
        }

        private Reservation add(
                long id,
                ReservationStatus status,
                Instant scheduledStart) throws Exception {
            if (clientUser == null) {
                clientUser = UserAccount.client(
                        "ana@example.com", "{noop}password", NOW);
                clientUser.verify(NOW);
                setId(clientUser, 900L);
            }
            Client client = Client.create(
                    clientUser, "Ana", "+56911111111", NOW);
            setId(client, 901L);
            Reservation reservation = Reservation.create(
                    client, scheduledStart, status, null, NOW);
            setId(reservation, id);
            reservationsById.put(id, reservation);
            candidates.add(new Candidate(
                    id, scheduledStart, clientUser.getId()));
            return reservation;
        }

        private int insertNotification(Object[] arguments)
                throws Exception {
            String key = (String) arguments[5];
            if (notificationsByKey.containsKey(key)) {
                return 0;
            }
            Reservation reservation =
                    reservationsById.get((Long) arguments[4]);
            Notification notification = Notification.create(
                    clientUser,
                    NotificationType.valueOf((String) arguments[1]),
                    (String) arguments[2],
                    (String) arguments[3],
                    reservation,
                    key,
                    (Instant) arguments[6]);
            setId(notification, 1_000L + notificationsByKey.size());
            notificationsByKey.put(key, notification);
            return 1;
        }

        private List<Notification> reminders() {
            return notificationsByKey.values().stream()
                    .filter(notification -> notification.getType()
                            == NotificationType.APPOINTMENT_REMINDER)
                    .toList();
        }

        private List<Long> reminderReservationIds() {
            return reminders().stream()
                    .map(notification ->
                            notification.getReservation().getId())
                    .toList();
        }
    }

    private record Candidate(
            Long reservationId,
            Instant scheduledStart,
            Long recipientUserId)
            implements ReservationRepository.ReminderCandidate {

        @Override
        public Long getReservationId() {
            return reservationId;
        }

        @Override
        public Instant getScheduledStart() {
            return scheduledStart;
        }

        @Override
        public Long getRecipientUserId() {
            return recipientUserId;
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(
            Class<T> type,
            Invocation invocation) {
        return (T) Proxy.newProxyInstance(
                type.getClassLoader(),
                new Class<?>[]{type},
                (proxy, method, arguments) -> {
                    if (method.getDeclaringClass() == Object.class) {
                        return switch (method.getName()) {
                            case "toString" ->
                                    type.getSimpleName() + "Proxy";
                            case "hashCode" ->
                                    System.identityHashCode(proxy);
                            case "equals" -> proxy == arguments[0];
                            default -> null;
                        };
                    }
                    return invocation.invoke(method, arguments);
                });
    }

    private static UnsupportedOperationException unsupported(
            String methodName) {
        return new UnsupportedOperationException(methodName);
    }

    private static void setId(Object entity, long id) throws Exception {
        Field field = entity.getClass().getDeclaredField("id");
        field.setAccessible(true);
        field.set(entity, id);
    }

    @FunctionalInterface
    private interface Invocation {
        Object invoke(java.lang.reflect.Method method, Object[] arguments)
                throws Throwable;
    }
}
