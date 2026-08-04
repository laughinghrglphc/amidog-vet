package com.amidog.app.notification;

import com.amidog.app.auth.AccountPrincipal;
import com.amidog.app.auth.AccountType;
import com.amidog.app.auth.UserAccount;
import com.amidog.app.auth.UserAccountRepository;
import com.amidog.app.client.Client;
import com.amidog.app.client.ClientRepository;
import com.amidog.app.client.CurrentClient;
import com.amidog.app.common.api.NotFoundException;
import com.amidog.app.reservation.Reservation;
import com.amidog.app.reservation.ReservationActor;
import com.amidog.app.reservation.ReservationRepository;
import com.amidog.app.reservation.ReservationStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;

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
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NotificationServiceTests {

    private static final Instant NOW =
            Instant.parse("2026-07-29T12:00:00Z");

    @Test
    void sequentialDuplicateCreationReturnsTheSameNotification()
            throws Exception {
        Fixture fixture = fixture(List.of(admin()));

        Notification first = fixture.service().onReservationCreated(
                fixture.reservation());
        Notification second = fixture.service().onReservationCreated(
                fixture.reservation());

        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(fixture.insertedKeys()).singleElement()
                .satisfies(key -> {
                    assertThat(key).contains("NEW_RESERVATION");
                    assertThat(key.length()).isLessThanOrEqualTo(160);
                });
        assertThat(first.getRecipient().getId()).isEqualTo(99L);
        assertThat(first.getType())
                .isEqualTo(NotificationType.NEW_RESERVATION);
    }

    @Test
    void unrelatedIntegrityFailuresAreNotRelabeled() throws Exception {
        Fixture fixture = fixture(List.of(admin()));
        DataIntegrityViolationException failure =
                new DataIntegrityViolationException("unrelated");
        fixture.insertFailure().set(failure);

        assertThatThrownBy(() -> fixture.service()
                .onReservationCreated(fixture.reservation()))
                .isSameAs(failure);
    }

    @Test
    void missingOrUnexpectedAdministratorFailsClosed() throws Exception {
        Fixture missing = fixture(List.of());
        Fixture multiple = fixture(List.of(admin(), secondAdmin()));

        assertThatThrownBy(() -> missing.service()
                .onReservationCreated(missing.reservation()))
                .isInstanceOf(NotificationInvariantException.class);
        assertThatThrownBy(() -> multiple.service()
                .onReservationCreated(multiple.reservation()))
                .isInstanceOf(NotificationInvariantException.class);
        assertThat(missing.insertedKeys()).isEmpty();
        assertThat(multiple.insertedKeys()).isEmpty();
    }

    @Test
    void separateClientReschedulesUseTheirImmutableEventIds()
            throws Exception {
        Fixture fixture = fixture(List.of(admin()));
        Reservation reservation = fixture.reservation();
        reservation.reschedule(
                reservation.getScheduledStart().plusSeconds(1800),
                ReservationStatus.PENDING,
                ReservationActor.CLIENT,
                NOW.plusSeconds(1));
        setId(lastEvent(reservation), 401L);
        fixture.service().onClientRescheduled(reservation);
        reservation.reschedule(
                reservation.getScheduledStart().plusSeconds(1800),
                ReservationStatus.PENDING,
                ReservationActor.CLIENT,
                NOW.plusSeconds(2));
        setId(lastEvent(reservation), 402L);
        fixture.service().onClientRescheduled(reservation);

        assertThat(fixture.insertedKeys()).hasSize(2);
        assertThat(fixture.insertedKeys().get(0)).contains("e:401");
        assertThat(fixture.insertedKeys().get(1)).contains("e:402");
    }

    @Test
    void reminderScanUsesOneInstantWindowAndNewStartsGetNewKeys()
            throws Exception {
        Fixture fixture = fixture(List.of(admin()));
        Reservation reservation = fixture.reservation();
        setStatus(reservation, ReservationStatus.CONFIRMED);
        setScheduledStart(reservation, NOW.plus(Duration.ofHours(24)));
        fixture.addReminderCandidate(reservation);

        int first = fixture.service().createAppointmentReminders();
        int repeated = fixture.service().createAppointmentReminders();
        reservation.reschedule(
                NOW.plus(Duration.ofHours(24))
                        .plus(Duration.ofMinutes(5)),
                ReservationStatus.CONFIRMED,
                ReservationActor.ADMIN,
                NOW.plusSeconds(5));
        fixture.reminderCandidates().clear();
        fixture.addReminderCandidate(reservation);
        fixture.service().createAppointmentReminders();

        assertThat(first).isEqualTo(1);
        assertThat(repeated).isZero();
        assertThat(fixture.reminderFrom().get())
                .isEqualTo(NOW.plus(Duration.ofHours(23)));
        assertThat(fixture.reminderTo().get())
                .isEqualTo(NOW.plus(Duration.ofHours(25)));
        assertThat(fixture.reminderPage().get().getPageSize()).isEqualTo(100);
        assertThat(fixture.reminderCalls()).containsExactly(
                "candidate-query", "reminder-lock-20",
                "candidate-query", "reminder-lock-20",
                "candidate-query", "reminder-lock-20");
        assertThat(fixture.insertedKeys())
                .filteredOn(key -> key.startsWith("APPOINTMENT_REMINDER"))
                .hasSize(2)
                .doesNotHaveDuplicates();
    }

    @Test
    void reminderScanRevalidatesLockedStatusAndScheduledStart()
            throws Exception {
        Fixture fixture = fixture(List.of(admin()));
        Reservation reservation = fixture.reservation();
        setStatus(reservation, ReservationStatus.CONFIRMED);
        setScheduledStart(reservation, NOW.plus(Duration.ofHours(24)));
        fixture.addReminderCandidate(reservation);
        setStatus(reservation, ReservationStatus.CANCELLED);

        assertThat(fixture.service().createAppointmentReminders()).isZero();
        assertThat(fixture.insertedKeys()).isEmpty();

        Fixture rescheduled = fixture(List.of(admin()));
        Reservation moved = rescheduled.reservation();
        setStatus(moved, ReservationStatus.CONFIRMED);
        setScheduledStart(moved, NOW.plus(Duration.ofHours(24)));
        rescheduled.addReminderCandidate(moved);
        moved.reschedule(
                NOW.plus(Duration.ofHours(24))
                        .plus(Duration.ofMinutes(5)),
                ReservationStatus.CONFIRMED,
                ReservationActor.ADMIN,
                NOW.plusSeconds(2));

        assertThat(rescheduled.service().createAppointmentReminders())
                .isZero();
        assertThat(rescheduled.insertedKeys()).isEmpty();
    }

    @Test
    void reminderScanCanBeDisabledWithoutReadingCandidates()
            throws Exception {
        Fixture disabled = fixture(
                List.of(admin()),
                new NotificationReminderProperties(false, 23, 25, 100));
        setStatus(disabled.reservation(), ReservationStatus.CONFIRMED);
        disabled.addReminderCandidate(disabled.reservation());

        assertThat(disabled.service().createAppointmentReminders()).isZero();
        assertThat(disabled.reminderFrom()).hasValue(null);
        assertThat(disabled.reminderCalls()).isEmpty();
    }

    @Test
    void reminderScanRevalidatesWindowFutureAndRecipient()
            throws Exception {
        Fixture wrongRecipient = fixture(List.of(admin()));
        Reservation reservation = wrongRecipient.reservation();
        setStatus(reservation, ReservationStatus.CONFIRMED);
        setScheduledStart(reservation, NOW.plus(Duration.ofHours(24)));
        wrongRecipient.addReminderCandidateSnapshot(
                reservation,
                reservation.getScheduledStart(),
                9_999L);

        assertThat(wrongRecipient.service()
                .createAppointmentReminders()).isZero();

        Fixture outsideWindow = fixture(List.of(admin()));
        Reservation outside = outsideWindow.reservation();
        setStatus(outside, ReservationStatus.CONFIRMED);
        setScheduledStart(
                outside,
                NOW.plus(Duration.ofHours(25)));
        outsideWindow.addReminderCandidate(outside);

        assertThat(outsideWindow.service()
                .createAppointmentReminders()).isZero();
        assertThat(wrongRecipient.insertedKeys()).isEmpty();
        assertThat(outsideWindow.insertedKeys()).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({
            "1, 2, 1, 'Tienes una reserva programada para dentro de aproximadamente 1 hora.'",
            "23, 25, 24, 'Tienes una reserva programada para dentro de aproximadamente 24 horas.'",
            "47, 49, 48, 'Tienes una reserva programada para dentro de aproximadamente 48 horas.'"
    })
    void reminderBodyReflectsTheConfiguredWindowMidpoint(
            int windowStartHours,
            int windowEndHours,
            int midpointHours,
            String expectedBody) throws Exception {
        Fixture fixture = fixture(
                List.of(admin()),
                new NotificationReminderProperties(
                        true, windowStartHours, windowEndHours, 100));
        Reservation reservation = fixture.reservation();
        setStatus(reservation, ReservationStatus.CONFIRMED);
        setScheduledStart(
                reservation,
                NOW.plus(Duration.ofHours(midpointHours)));
        fixture.addReminderCandidate(reservation);

        assertThat(fixture.service().createAppointmentReminders())
                .isEqualTo(1);
        assertThat(fixture.stored().values())
                .singleElement()
                .extracting(Notification::getBody)
                .isEqualTo(expectedBody);
    }

    @Test
    void recipientAccessIsBoundedScopedOrderedAndReadIdempotently()
            throws Exception {
        Fixture fixture = fixture(List.of(admin()));
        Notification older = fixture.persistForClient(
                "older", NOW.minusSeconds(20));
        Notification newer = fixture.persistForClient(
                "newer", NOW.minusSeconds(10));
        fixture.listRows().addAll(List.of(newer, older));
        AccountPrincipal client = clientPrincipal();

        List<NotificationDtos.NotificationResponse> listed =
                fixture.service().listClient(client);
        NotificationDtos.NotificationResponse firstRead =
                fixture.service().markClientRead(client, newer.getId());
        NotificationDtos.NotificationResponse repeated =
                fixture.service().markClientRead(client, newer.getId());
        NotificationDtos.ReadAllResponse readAll =
                fixture.service().markAllClientRead(client);

        assertThat(listed).extracting(NotificationDtos.NotificationResponse::id)
                .containsExactly(newer.getId(), older.getId());
        assertThat(fixture.listUserId()).hasValue(10L);
        assertThat(fixture.listPage().get().getPageSize()).isEqualTo(100);
        assertThat(firstRead.unread()).isFalse();
        assertThat(repeated.unread()).isFalse();
        assertThat(newer.getReadAt()).isEqualTo(NOW);
        assertThat(readAll.markedRead()).isEqualTo(1);
        assertThat(fixture.readAllUserId()).hasValue(10L);
        assertThat(fixture.readCalls()).containsExactly(
                "mark-one-701", "find-one-701",
                "mark-one-701", "find-one-701",
                "read-all");
    }

    @Test
    void foreignOrMissingNotificationUsesTheSameNonEnumeratingNotFound()
            throws Exception {
        Fixture fixture = fixture(List.of(admin()));

        assertThatThrownBy(() -> fixture.service()
                .markClientRead(clientPrincipal(), 9_999L))
                .isInstanceOf(NotFoundException.class)
                .hasMessage(NotificationService.NOTIFICATION_NOT_FOUND);
    }

    @Test
    void inactiveClientCannotListNotifications() throws Exception {
        Fixture fixture = fixture(List.of(admin()));
        fixture.deactivateClient();

        assertThatThrownBy(() ->
                fixture.service().listClient(clientPrincipal()))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("No se encontr\u00f3 el perfil de cliente.");
        assertThat(fixture.listUserId()).hasValue(null);
    }

    @Test
    void inactiveClientCannotMarkOwnedOrForeignNotificationRead()
            throws Exception {
        Fixture fixture = fixture(List.of(admin()));
        Notification owned = fixture.persistForClient("owned", NOW);
        fixture.deactivateClient();

        assertThatThrownBy(() -> fixture.service()
                .markClientRead(clientPrincipal(), owned.getId()))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("No se encontr\u00f3 el perfil de cliente.");
        assertThatThrownBy(() -> fixture.service()
                .markClientRead(clientPrincipal(), 9_999L))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("No se encontr\u00f3 el perfil de cliente.");
        assertThat(fixture.readCalls()).isEmpty();
        assertThat(owned.getReadAt()).isNull();
    }

    @Test
    void inactiveClientCannotMarkAllNotificationsRead()
            throws Exception {
        Fixture fixture = fixture(List.of(admin()));
        Notification owned = fixture.persistForClient("owned", NOW);
        fixture.deactivateClient();

        assertThatThrownBy(() -> fixture.service()
                .markAllClientRead(clientPrincipal()))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("No se encontr\u00f3 el perfil de cliente.");
        assertThat(fixture.readCalls()).isEmpty();
        assertThat(owned.getReadAt()).isNull();
    }

    @Test
    void reactivationRestoresRecipientScopedNotificationAccess()
            throws Exception {
        Fixture fixture = fixture(List.of(admin()));
        Notification owned = fixture.persistForClient("owned", NOW);
        fixture.listRows().add(owned);
        fixture.deactivateClient();
        assertThatThrownBy(() ->
                fixture.service().listClient(clientPrincipal()))
                .isInstanceOf(NotFoundException.class);

        fixture.reactivateClient();

        assertThat(fixture.service().listClient(clientPrincipal()))
                .extracting(NotificationDtos.NotificationResponse::id)
                .containsExactly(owned.getId());
        assertThatThrownBy(() -> fixture.service()
                .markClientRead(clientPrincipal(), 9_999L))
                .isInstanceOf(NotFoundException.class)
                .hasMessage(NotificationService.NOTIFICATION_NOT_FOUND);
        assertThat(fixture.service()
                .markClientRead(clientPrincipal(), owned.getId()).unread())
                .isFalse();
    }

    private static Fixture fixture(List<UserAccount> admins)
            throws Exception {
        return fixture(
                admins,
                new NotificationReminderProperties(true, 23, 25, 100));
    }

    private static Fixture fixture(
            List<UserAccount> admins,
            NotificationReminderProperties properties) throws Exception {
        UserAccount clientUser = UserAccount.client(
                "ana@example.com", "{noop}password", NOW);
        clientUser.verify(NOW);
        setId(clientUser, 10L);
        Client client = Client.create(
                clientUser, "Ana", "+56911111111", NOW);
        setId(client, 11L);
        Reservation reservation = Reservation.create(
                client,
                NOW.plus(Duration.ofDays(3)),
                ReservationStatus.PENDING,
                "Información privada que no debe notificarse",
                NOW);
        setId(reservation, 20L);

        AtomicLong ids = new AtomicLong(500);
        Map<String, Notification> stored = new HashMap<>();
        List<String> insertedKeys = new ArrayList<>();
        List<Notification> listRows = new ArrayList<>();
        List<ReservationRepository.ReminderCandidate>
                reminderCandidates = new ArrayList<>();
        Map<Long, Reservation> reminderLockedRows = new HashMap<>();
        List<String> reminderCalls = new ArrayList<>();
        List<String> readCalls = new ArrayList<>();
        AtomicReference<RuntimeException> insertFailure =
                new AtomicReference<>();
        AtomicReference<Long> listUserId = new AtomicReference<>();
        AtomicReference<Pageable> listPage = new AtomicReference<>();
        AtomicReference<Long> readAllUserId = new AtomicReference<>();
        AtomicReference<Instant> reminderFrom = new AtomicReference<>();
        AtomicReference<Instant> reminderTo = new AtomicReference<>();
        AtomicReference<Pageable> reminderPage = new AtomicReference<>();

        NotificationRepository notifications = proxy(
                NotificationRepository.class, (method, args) -> {
                    return switch (method.getName()) {
                        case "insertIgnoringDeduplicationConflict" -> {
                            if (insertFailure.get() != null) {
                                throw insertFailure.get();
                            }
                            String key = (String) args[5];
                            if (stored.containsKey(key)) {
                                yield 0;
                            }
                            long recipientId = (Long) args[0];
                            UserAccount recipient = recipientId == 10L
                                    ? clientUser
                                    : admins.stream()
                                            .filter(user -> user.getId()
                                                    .equals(recipientId))
                                            .findFirst().orElseThrow();
                            Notification created = Notification.create(
                                    recipient,
                                    NotificationType.valueOf((String) args[1]),
                                    (String) args[2],
                                    (String) args[3],
                                    reservation,
                                    key,
                                    (Instant) args[6]);
                            setId(created, ids.incrementAndGet());
                            stored.put(key, created);
                            insertedKeys.add(key);
                            yield 1;
                        }
                        case "findByDeduplicationKey" ->
                                Optional.ofNullable(stored.get(args[0]));
                        case "findAllByRecipientIdOrderByCreatedAtDescIdDesc" -> {
                            listUserId.set((Long) args[0]);
                            listPage.set((Pageable) args[1]);
                            yield List.copyOf(listRows);
                        }
                        case "markOneUnread" -> {
                            long id = (Long) args[0];
                            long recipientId = (Long) args[1];
                            Instant readAt = (Instant) args[2];
                            readCalls.add("mark-one-" + id);
                            Optional<Notification> owned =
                                    stored.values().stream()
                                            .filter(item -> item.getId() == id
                                                    && item.getRecipient()
                                                    .getId() == recipientId)
                                            .findFirst();
                            if (owned.isPresent()
                                    && owned.get().getReadAt() == null) {
                                owned.get().markRead(readAt);
                                yield 1;
                            }
                            yield 0;
                        }
                        case "findByIdAndRecipientId" -> {
                            long id = (Long) args[0];
                            long recipientId = (Long) args[1];
                            readCalls.add("find-one-" + id);
                            yield stored.values().stream()
                                    .filter(item -> item.getId() == id
                                            && item.getRecipient().getId()
                                            == recipientId)
                                    .findFirst();
                        }
                        case "markAllUnread" -> {
                            readCalls.add("read-all");
                            readAllUserId.set((Long) args[0]);
                            Instant readAt = (Instant) args[1];
                            int count = 0;
                            for (Notification item : stored.values()) {
                                if (item.getRecipient().getId()
                                        .equals(args[0])
                                        && item.getReadAt() == null) {
                                    item.markRead(readAt);
                                    count++;
                                }
                            }
                            yield count;
                        }
                        default -> throw unsupported(method);
                    };
                });
        UserAccountRepository users = proxy(
                UserAccountRepository.class, (method, args) -> {
                    if (method.getName().equals("findAllByAccountType")) {
                        return admins;
                    }
                    throw unsupported(method);
                });
        ReservationRepository reservations = proxy(
                ReservationRepository.class, (method, args) -> {
                    if (method.getName().equals(
                            "findConfirmedReminderCandidates")) {
                        reminderCalls.add("candidate-query");
                        reminderFrom.set((Instant) args[0]);
                        reminderTo.set((Instant) args[1]);
                        reminderPage.set((Pageable) args[2]);
                        return List.copyOf(reminderCandidates);
                    }
                    if (method.getName().equals(
                            "findReminderForUpdate")) {
                        long id = (Long) args[0];
                        reminderCalls.add("reminder-lock-" + id);
                        return Optional.ofNullable(
                                reminderLockedRows.get(id));
                    }
                    throw unsupported(method);
                });
        ClientRepository clients = proxy(
                ClientRepository.class, (method, args) -> {
                    if (method.getName().equals(
                            "findByUserIdAndActiveTrue")) {
                        return Optional.ofNullable(
                                client.isActive()
                                        && client.getUser().getId()
                                        .equals(args[0])
                                        ? client
                                        : null);
                    }
                    throw unsupported(method);
                });
        NotificationService service = new NotificationService(
                notifications,
                users,
                reservations,
                new CurrentClient(clients),
                Clock.fixed(NOW, ZoneOffset.UTC),
                properties);
        return new Fixture(
                service, reservation, client, clientUser, stored, insertedKeys,
                listRows, reminderCandidates, reminderLockedRows,
                reminderCalls, readCalls, insertFailure, listUserId, listPage,
                readAllUserId, reminderFrom, reminderTo, reminderPage);
    }

    private static UserAccount admin() throws Exception {
        UserAccount user = UserAccount.admin(
                "admin@example.com", "{noop}password", NOW);
        setId(user, 99L);
        return user;
    }

    private static UserAccount secondAdmin() throws Exception {
        UserAccount user = UserAccount.admin(
                "other-admin@example.com", "{noop}password", NOW);
        setId(user, 100L);
        return user;
    }

    private static AccountPrincipal clientPrincipal() {
        return new AccountPrincipal(
                10L, 11L, "ana@example.com", "Ana",
                AccountType.CLIENT, "{noop}password", true, true);
    }

    private static Object lastEvent(Reservation reservation) {
        return reservation.getEvents()
                .get(reservation.getEvents().size() - 1);
    }

    private static void setStatus(
            Reservation reservation, ReservationStatus status)
            throws Exception {
        Field field = Reservation.class.getDeclaredField("status");
        field.setAccessible(true);
        field.set(reservation, status);
    }

    private static void setScheduledStart(
            Reservation reservation, Instant start) throws Exception {
        Field field = Reservation.class.getDeclaredField("scheduledStart");
        field.setAccessible(true);
        field.set(reservation, start);
    }

    private static void setId(Object entity, long id) throws Exception {
        Field field = entity.getClass().getDeclaredField("id");
        field.setAccessible(true);
        field.set(entity, id);
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, Invocation invocation) {
        return (T) Proxy.newProxyInstance(
                type.getClassLoader(),
                new Class<?>[]{type},
                (proxy, method, args) -> {
                    if (method.getDeclaringClass() == Object.class) {
                        return switch (method.getName()) {
                            case "toString" ->
                                    type.getSimpleName() + "Proxy";
                            case "hashCode" ->
                                    System.identityHashCode(proxy);
                            case "equals" -> proxy == args[0];
                            default -> null;
                        };
                    }
                    return invocation.invoke(method, args);
                });
    }

    private static UnsupportedOperationException unsupported(
            java.lang.reflect.Method method) {
        return new UnsupportedOperationException(method.getName());
    }

    private record Fixture(
            NotificationService service,
            Reservation reservation,
            Client client,
            UserAccount clientUser,
            Map<String, Notification> stored,
            List<String> insertedKeys,
            List<Notification> listRows,
            List<ReservationRepository.ReminderCandidate>
                    reminderCandidates,
            Map<Long, Reservation> reminderLockedRows,
            List<String> reminderCalls,
            List<String> readCalls,
            AtomicReference<RuntimeException> insertFailure,
            AtomicReference<Long> listUserId,
            AtomicReference<Pageable> listPage,
            AtomicReference<Long> readAllUserId,
            AtomicReference<Instant> reminderFrom,
            AtomicReference<Instant> reminderTo,
            AtomicReference<Pageable> reminderPage) {

        void deactivateClient() {
            client.updateByAdministrator(
                    client.getName(), client.getPhone(), false, NOW);
        }

        void reactivateClient() {
            client.updateByAdministrator(
                    client.getName(), client.getPhone(), true, NOW);
        }

        void addReminderCandidate(Reservation reservation) {
            addReminderCandidateSnapshot(
                    reservation,
                    reservation.getScheduledStart(),
                    reservation.getClient().getUser().getId());
        }

        void addReminderCandidateSnapshot(
                Reservation reservation,
                Instant scheduledStart,
                long recipientUserId) {
            long reservationId = reservation.getId();
            reminderCandidates.add(
                    new ReservationRepository.ReminderCandidate() {
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
                    });
            reminderLockedRows.put(reservationId, reservation);
        }

        Notification persistForClient(String suffix, Instant createdAt)
                throws Exception {
            Notification item = Notification.create(
                    clientUser,
                    NotificationType.RESERVATION_CONFIRMED,
                    "Reserva confirmada",
                    "Tu reserva fue confirmada.",
                    reservation,
                    "fixture:" + suffix,
                    createdAt);
            setId(item, 700L + stored.size());
            stored.put(item.getDeduplicationKey(), item);
            return item;
        }
    }

    @FunctionalInterface
    private interface Invocation {
        Object invoke(java.lang.reflect.Method method, Object[] args)
                throws Throwable;
    }
}
