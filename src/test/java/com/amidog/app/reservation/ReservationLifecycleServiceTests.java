package com.amidog.app.reservation;

import com.amidog.app.auth.AccountPrincipal;
import com.amidog.app.auth.AccountType;
import com.amidog.app.auth.UserAccount;
import com.amidog.app.client.Client;
import com.amidog.app.client.ClientRepository;
import com.amidog.app.client.CurrentClient;
import com.amidog.app.common.api.ConflictException;
import com.amidog.app.common.api.ConflictType;
import com.amidog.app.common.api.NotFoundException;
import com.amidog.app.config.AmidogProperties;
import com.amidog.app.notification.Notification;
import com.amidog.app.notification.NotificationOperations;
import com.amidog.app.scheduling.AvailabilityDtos;
import com.amidog.app.scheduling.AvailabilityService;
import com.amidog.app.scheduling.ClinicTime;
import com.amidog.app.scheduling.OccupiedSlotReader;
import org.junit.jupiter.api.Test;
import org.postgresql.util.PSQLException;
import org.postgresql.util.ServerErrorMessage;
import org.springframework.dao.DataIntegrityViolationException;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.amidog.app.reservation.ReservationLifecycleDtos.CancelReservationRequest;
import static com.amidog.app.reservation.ReservationLifecycleDtos.RescheduleRequest;
import static com.amidog.app.reservation.ReservationLifecycleDtos.StatusChangeRequest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReservationLifecycleServiceTests {

    private static final Instant NOW =
            Instant.parse("2026-07-29T12:00:00Z");
    private static final OffsetDateTime ORIGINAL =
            OffsetDateTime.parse("2026-08-10T10:00:00-04:00");
    private static final OffsetDateTime TARGET =
            OffsetDateTime.parse("2026-08-10T11:00:00-04:00");

    @Test
    void clientCancellationLocksThenMutatesAndFlushesOwnedFutureReservation()
            throws Exception {
        Fixture fixture = fixture(
                ReservationStatus.CONFIRMED, false, ORIGINAL, true);

        var response = fixture.service().cancelClient(
                fixture.clientPrincipal(),
                20L,
                new CancelReservationRequest("  No podré asistir  "));

        assertThat(response.status())
                .isEqualTo(ReservationStatus.CANCELLED);
        assertThat(response.cancelledBy())
                .isEqualTo(ReservationActor.CLIENT);
        assertThat(response.reason()).isEqualTo("No podré asistir");
        assertThat(fixture.calls()).containsExactly(
                "scheduling-lock", "owned-reservation-lock", "write",
                "notification:client-cancelled");
        assertThat(fixture.reservation().getEvents()).singleElement()
                .satisfies(event -> {
                    assertThat(event.getEventType()).isEqualTo("CANCELLED");
                    assertThat(event.getActorType()).isEqualTo("CLIENT");
                });
    }

    @Test
    void clientMissingOrForeignReservationUsesTheSameNotFoundWithoutMutation()
            throws Exception {
        Fixture fixture = fixture(
                ReservationStatus.PENDING, false, ORIGINAL, false);

        assertThatThrownBy(() -> fixture.service().cancelClient(
                fixture.clientPrincipal(), 20L,
                new CancelReservationRequest(null)))
                .isInstanceOf(NotFoundException.class)
                .hasMessage(ReservationLifecycleService.RESERVATION_NOT_FOUND);

        assertThat(fixture.reservation().getStatus())
                .isEqualTo(ReservationStatus.PENDING);
        assertThat(fixture.reservation().getEvents()).isEmpty();
        assertThat(fixture.calls()).containsExactly(
                "scheduling-lock", "owned-reservation-lock");
    }

    @Test
    void clientCannotCancelAnAppointmentStartingNow() throws Exception {
        Fixture fixture = fixture(
                ReservationStatus.PENDING,
                false,
                OffsetDateTime.ofInstant(
                        NOW, ZoneId.of("America/Santiago")),
                true);

        assertThatThrownBy(() -> fixture.service().cancelClient(
                fixture.clientPrincipal(), 20L,
                new CancelReservationRequest(null)))
                .isInstanceOf(ConflictException.class)
                .satisfies(failure -> assertThat(
                        ((ConflictException) failure).getType())
                        .isEqualTo(
                                ConflictType.RESERVATION_NOT_IN_FUTURE));

        assertThat(fixture.reservation().getEvents()).isEmpty();
        assertThat(fixture.calls()).doesNotContain("write");
    }

    @Test
    void clientRescheduleUsesConfiguredPendingStatusAndExcludesItsOldRow()
            throws Exception {
        Fixture fixture = fixture(
                ReservationStatus.CONFIRMED, false, ORIGINAL, true);

        var response = fixture.service().rescheduleClient(
                fixture.clientPrincipal(),
                20L,
                new RescheduleRequest(TARGET));

        assertThat(response.previousStatus())
                .isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(response.status()).isEqualTo(ReservationStatus.PENDING);
        assertThat(response.previousStartsAt()).isEqualTo(ORIGINAL);
        assertThat(response.startsAt()).isEqualTo(TARGET);
        assertThat(response.endsAt()).isEqualTo(TARGET.plusMinutes(30));
        assertThat(fixture.calls()).containsExactly(
                "scheduling-lock", "owned-reservation-lock",
                "availability-excluding-20", "write",
                "notification:client-rescheduled");
        assertThat(fixture.reservation().getClientNote()).isEqualTo("Control");
    }

    @Test
    void clientRescheduleCanReapplyAutoConfirmation() throws Exception {
        Fixture fixture = fixture(
                ReservationStatus.PENDING, true, ORIGINAL, true);

        assertThat(fixture.service().rescheduleClient(
                fixture.clientPrincipal(), 20L,
                new RescheduleRequest(TARGET)).status())
                .isEqualTo(ReservationStatus.CONFIRMED);
    }

    @Test
    void administratorReschedulePreservesPendingOrConfirmedStatus()
            throws Exception {
        Fixture confirmed = fixture(
                ReservationStatus.CONFIRMED, false, ORIGINAL, true);
        Fixture pending = fixture(
                ReservationStatus.PENDING, true, ORIGINAL, true);

        assertThat(confirmed.service().rescheduleAdmin(
                confirmed.adminPrincipal(), 20L,
                new RescheduleRequest(TARGET)).status())
                .isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(confirmed.calls())
                .contains("notification:admin-rescheduled");
        assertThat(pending.service().rescheduleAdmin(
                pending.adminPrincipal(), 20L,
                new RescheduleRequest(TARGET)).status())
                .isEqualTo(ReservationStatus.PENDING);
        assertThat(pending.calls())
                .contains("notification:admin-rescheduled");
    }

    @Test
    void administratorStatusUsesTheExplicitMatrixAndAdminActor()
            throws Exception {
        Fixture fixture = fixture(
                ReservationStatus.PENDING, false, ORIGINAL, true);

        var response = fixture.service().changeStatusAdmin(
                fixture.adminPrincipal(),
                20L,
                new StatusChangeRequest(
                        ReservationStatus.CONFIRMED,
                        "  Confirmada por teléfono  "));

        assertThat(response.previousStatus())
                .isEqualTo(ReservationStatus.PENDING);
        assertThat(response.status())
                .isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(response.reason())
                .isEqualTo("Confirmada por teléfono");
        assertThat(fixture.reservation().getEvents()).singleElement()
                .satisfies(event -> assertThat(event.getActorType())
                        .isEqualTo("ADMIN"));
        assertThat(fixture.calls())
                .contains("notification:reservation-confirmed");
    }

    @Test
    void administratorCancellationNotifiesTheClient() throws Exception {
        Fixture fixture = fixture(
                ReservationStatus.CONFIRMED, false, ORIGINAL, true);

        fixture.service().changeStatusAdmin(
                fixture.adminPrincipal(),
                20L,
                new StatusChangeRequest(
                        ReservationStatus.CANCELLED, null));

        assertThat(fixture.calls())
                .contains("notification:admin-cancelled");
    }

    @Test
    void completedAndNoShowChangesDoNotCreateUserFacingNoise()
            throws Exception {
        Fixture completed = fixture(
                ReservationStatus.CONFIRMED, false, ORIGINAL, true);
        Fixture noShow = fixture(
                ReservationStatus.CONFIRMED, false, ORIGINAL, true);

        completed.service().changeStatusAdmin(
                completed.adminPrincipal(),
                20L,
                new StatusChangeRequest(
                        ReservationStatus.COMPLETED, null));
        noShow.service().changeStatusAdmin(
                noShow.adminPrincipal(),
                20L,
                new StatusChangeRequest(
                        ReservationStatus.NO_SHOW, null));

        assertThat(completed.calls())
                .noneMatch(call -> call.startsWith("notification:"));
        assertThat(noShow.calls())
                .noneMatch(call -> call.startsWith("notification:"));
    }

    @Test
    void wrongChileanOffsetIsRejectedBeforeAvailabilityOrMutation()
            throws Exception {
        Fixture fixture = fixture(
                ReservationStatus.PENDING, false, ORIGINAL, true);
        OffsetDateTime wrongOffset = OffsetDateTime.of(
                TARGET.toLocalDateTime(), ZoneOffset.ofHours(-3));

        assertThatThrownBy(() -> fixture.service().rescheduleClient(
                fixture.clientPrincipal(), 20L,
                new RescheduleRequest(wrongOffset)))
                .isInstanceOf(
                        com.amidog.app.scheduling
                                .InvalidSchedulingRequestException.class);
        assertThat(fixture.calls())
                .containsExactly(
                        "scheduling-lock", "owned-reservation-lock");
        assertThat(fixture.reservation().getEvents()).isEmpty();
    }

    @Test
    void exactExclusionCollisionIsTranslatedButUnrelatedIntegrityFailureEscapes()
            throws Exception {
        Fixture collision = fixture(
                ReservationStatus.PENDING, false, ORIGINAL, true);
        collision.failWrite(violation(
                "23P01", "reservations_no_occupied_overlap"));
        assertThatThrownBy(() -> collision.service().rescheduleClient(
                collision.clientPrincipal(), 20L,
                new RescheduleRequest(TARGET)))
                .isInstanceOf(ConflictException.class)
                .satisfies(failure -> assertThat(
                        ((ConflictException) failure).getType())
                        .isEqualTo(ConflictType.SLOT_ALREADY_BOOKED));

        Fixture unrelated = fixture(
                ReservationStatus.PENDING, false, ORIGINAL, true);
        DataIntegrityViolationException other = violation(
                "23505", "some_other_constraint");
        unrelated.failWrite(other);
        assertThatThrownBy(() -> unrelated.service().rescheduleClient(
                unrelated.clientPrincipal(), 20L,
                new RescheduleRequest(TARGET)))
                .isSameAs(other);
    }

    private Fixture fixture(
            ReservationStatus status,
            boolean autoConfirm,
            OffsetDateTime original,
            boolean owned) throws Exception {
        List<String> calls = new ArrayList<>();
        var writeFailure =
                new java.util.concurrent.atomic.AtomicReference<
                        RuntimeException>();
        AmidogProperties properties = properties(autoConfirm);
        ClinicTime clinicTime = new ClinicTime(properties);
        UserAccount account = UserAccount.client(
                "ana@example.com", "{noop}password", NOW);
        account.verify(NOW);
        Client client = Client.create(
                account, "Ana", "+56911111111", NOW);
        setId(account, 10L);
        setId(client, 11L);
        Reservation reservation = Reservation.create(
                client, original.toInstant(), status, "Control", NOW);
        setId(reservation, 20L);

        ClientRepository clients = proxy(
                ClientRepository.class, (method, args) -> {
                    if (method.getName().equals(
                            "findByUserIdAndActiveTrue")) {
                        return Optional.of(client);
                    }
                    throw unsupported(method);
                });
        ReservationRepository reservations = proxy(
                ReservationRepository.class, (method, args) -> {
                    if (method.getName().equals(
                            "findOwnedForUpdate")) {
                        calls.add("owned-reservation-lock");
                        return owned
                                ? Optional.of(reservation)
                                : Optional.empty();
                    }
                    if (method.getName().equals("findByIdForUpdate")) {
                        calls.add("admin-reservation-lock");
                        return Optional.of(reservation);
                    }
                    if (method.getName().equals("saveAndFlush")) {
                        calls.add("write");
                        if (writeFailure.get() != null) {
                            throw writeFailure.get();
                        }
                        return args[0];
                    }
                    throw unsupported(method);
                });
        AvailabilityService availability =
                new AvailabilityService(
                        null, null, null, clinicTime, properties,
                        Clock.fixed(NOW, ZoneOffset.UTC)) {
                    @Override
                    public List<AvailabilityDtos.AvailableSlotResponse>
                    availableSlots(
                            LocalDate from,
                            LocalDate to,
                            Long excludedReservationId) {
                        calls.add(
                                "availability-excluding-"
                                        + excludedReservationId);
                        return List.of(
                                new AvailabilityDtos.AvailableSlotResponse(
                                        TARGET, TARGET.plusMinutes(30)));
                    }
                };
        OccupiedSlotReader occupied = new OccupiedSlotReader(null) {
            @Override
            public List<OccupiedSlot> findOverlappingExcluding(
                    Instant start,
                    Instant end,
                    long excludedReservationId) {
                return List.of();
            }
        };
        ReservationLifecycleService service =
                new ReservationLifecycleService(
                        new CurrentClient(clients),
                        reservations,
                        availability,
                        clinicTime,
                        () -> calls.add("scheduling-lock"),
                        occupied,
                        properties,
                        Clock.fixed(NOW, ZoneOffset.UTC),
                        notificationOperations(calls));
        return new Fixture(
                service,
                reservation,
                calls,
                writeFailure);
    }

    private static AmidogProperties properties(boolean autoConfirm) {
        return new AmidogProperties(
                ZoneId.of("America/Santiago"),
                URI.create("http://localhost:5173"),
                URI.create("http://localhost:5173"),
                new AmidogProperties.Booking(
                        autoConfirm, 30, 0, 365),
                new AmidogProperties.Admin("", "", "", ""),
                new AmidogProperties.Contact("", ""),
                new AmidogProperties.Email("log"));
    }

    private static DataIntegrityViolationException violation(
            String state,
            String constraint) {
        ServerErrorMessage metadata = new ServerErrorMessage("") {
            @Override
            public String getSQLState() {
                return state;
            }

            @Override
            public String getConstraint() {
                return constraint;
            }
        };
        return new DataIntegrityViolationException(
                "sanitized wrapper", new PSQLException(metadata));
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(
            Class<T> type,
            Invocation invocation) {
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

    private static void setId(Object entity, long id) throws Exception {
        Field field = entity.getClass().getDeclaredField("id");
        field.setAccessible(true);
        field.set(entity, id);
    }

    private static UnsupportedOperationException unsupported(
            java.lang.reflect.Method method) {
        return new UnsupportedOperationException(method.getName());
    }

    private static NotificationOperations notificationOperations(
            List<String> calls) {
        return new NotificationOperations() {
            @Override
            public Notification onReservationCreated(
                    Reservation reservation) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Notification onClientCancelled(
                    Reservation reservation) {
                calls.add("notification:client-cancelled");
                return null;
            }

            @Override
            public Notification onClientRescheduled(
                    Reservation reservation) {
                calls.add("notification:client-rescheduled");
                return null;
            }

            @Override
            public Notification onReservationConfirmed(
                    Reservation reservation) {
                calls.add("notification:reservation-confirmed");
                return null;
            }

            @Override
            public Notification onAdminCancelled(
                    Reservation reservation) {
                calls.add("notification:admin-cancelled");
                return null;
            }

            @Override
            public Notification onAdminRescheduled(
                    Reservation reservation) {
                calls.add("notification:admin-rescheduled");
                return null;
            }
        };
    }

    private record Fixture(
            ReservationLifecycleService service,
            Reservation reservation,
            List<String> calls,
            java.util.concurrent.atomic.AtomicReference<RuntimeException>
                    writeFailure) {
        AccountPrincipal clientPrincipal() {
            return new AccountPrincipal(
                    10L, 999L, "ana@example.com", "Ana",
                    AccountType.CLIENT, "{noop}password", true, true);
        }

        AccountPrincipal adminPrincipal() {
            return new AccountPrincipal(
                    99L, null, "admin@example.com", "Admin",
                    AccountType.ADMIN, "{noop}password", true, true);
        }

        void failWrite(RuntimeException failure) {
            writeFailure.set(failure);
        }
    }

    @FunctionalInterface
    private interface Invocation {
        Object invoke(java.lang.reflect.Method method, Object[] args)
                throws Throwable;
    }
}
