package com.amidog.app.reservation;

import com.amidog.app.auth.AccountPrincipal;
import com.amidog.app.auth.AccountType;
import com.amidog.app.auth.UserAccount;
import com.amidog.app.catalog.ServiceOffering;
import com.amidog.app.catalog.ServiceOfferingRepository;
import com.amidog.app.client.Client;
import com.amidog.app.client.ClientRepository;
import com.amidog.app.client.CurrentClient;
import com.amidog.app.client.Pet;
import com.amidog.app.client.PetRepository;
import com.amidog.app.common.api.TooManyRequestsException;
import com.amidog.app.common.security.RateLimitService;
import com.amidog.app.config.AmidogProperties;
import com.amidog.app.notification.Notification;
import com.amidog.app.notification.NotificationOperations;
import com.amidog.app.scheduling.AvailabilityService;
import com.amidog.app.scheduling.ClinicTime;
import com.amidog.app.scheduling.OccupiedSlotReader;
import com.amidog.app.scheduling.InvalidSchedulingRequestException;
import com.amidog.app.scheduling.SchedulingBadRequestType;
import org.junit.jupiter.api.Test;

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
import java.util.concurrent.atomic.AtomicLong;

import static com.amidog.app.reservation.ReservationDtos.CreateReservationRequest;
import static com.amidog.app.reservation.ReservationDtos.ReservationItemRequest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReservationCreationServiceTests {

    private static final Instant NOW =
            Instant.parse("2026-07-29T12:00:00Z");
    private static final OffsetDateTime START =
            OffsetDateTime.parse("2026-08-10T10:00:00-04:00");

    @Test
    void locksOwnedRowsThenSchedulingBeforeFinalRecheckAndWrite()
            throws Exception {
        Fixture fixture = fixture(false);

        var response = fixture.service().create(
                fixture.principal(), request());

        assertThat(response.status()).isEqualTo(ReservationStatus.PENDING);
        assertThat(fixture.calls()).containsExactly(
                "pet-lock", "service-lock", "availability",
                "scheduling-lock", "availability", "write",
                "notification:new-reservation");
    }

    @Test
    void realAutoConfirmConfigurationControlsInitialStatus()
            throws Exception {
        Fixture fixture = fixture(true);

        assertThat(fixture.service().create(
                fixture.principal(), request()).status())
                .isEqualTo(ReservationStatus.CONFIRMED);
    }

    @Test
    void duplicatePetIsAClosedBadRequestBeforeAnyRowLock()
            throws Exception {
        Fixture fixture = fixture(false);
        CreateReservationRequest duplicate =
                new CreateReservationRequest(
                        START,
                        List.of(
                                new ReservationItemRequest(3L, 4L),
                                new ReservationItemRequest(3L, 4L)),
                        null);

        assertThatThrownBy(() ->
                fixture.service().create(fixture.principal(), duplicate))
                .isInstanceOf(InvalidSchedulingRequestException.class)
                .satisfies(failure -> assertThat(
                        ((InvalidSchedulingRequestException) failure)
                                .getType())
                        .isEqualTo(
                                SchedulingBadRequestType
                                        .DUPLICATE_RESERVATION_PET));
        assertThat(fixture.calls()).isEmpty();
    }

    @Test
    void eleventhReservationCreationWithinOneHourIsRateLimited()
            throws Exception {
        Fixture fixture = fixture(false);

        for (int attempt = 0; attempt < 10; attempt++) {
            fixture.service().create(fixture.principal(), request());
        }

        assertThatThrownBy(() ->
                fixture.service().create(fixture.principal(), request()))
                .isInstanceOf(TooManyRequestsException.class);
    }

    private Fixture fixture(boolean autoConfirm) throws Exception {
        List<String> calls = new ArrayList<>();
        AtomicLong ids = new AtomicLong(100);
        AmidogProperties properties = properties(autoConfirm);
        ClinicTime clinicTime = new ClinicTime(properties);

        UserAccount user = UserAccount.client(
                "ana@example.com", "{noop}password", NOW);
        user.verify(NOW);
        Client client = Client.create(
                user, "Ana", "+56911111111", NOW);
        Pet pet = Pet.create(
                client, "Milo", "Gato", null, null, NOW);
        ServiceOffering service = ServiceOffering.create(
                "consulta", "Consulta", null, 0, NOW);
        setId(user, 1L);
        setId(client, 2L);
        setId(pet, 3L);
        setId(service, 4L);

        ClientRepository clients = proxy(
                ClientRepository.class, (method, args) -> {
                    if (method.getName().equals(
                            "findByUserIdAndActiveTrue")) {
                        return Optional.of(client);
                    }
                    throw unsupported(method);
                });
        PetRepository pets = proxy(
                PetRepository.class, (method, args) -> {
                    if (method.getName().equals(
                            "findActiveOwnedForUpdate")) {
                        calls.add("pet-lock");
                        return Optional.of(pet);
                    }
                    throw unsupported(method);
                });
        ServiceOfferingRepository services = proxy(
                ServiceOfferingRepository.class, (method, args) -> {
                    if (method.getName().equals("findByIdForUpdate")) {
                        calls.add("service-lock");
                        return Optional.of(service);
                    }
                    throw unsupported(method);
                });
        ReservationRepository reservations = proxy(
                ReservationRepository.class, (method, args) -> {
                    if (method.getName().equals("saveAndFlush")) {
                        calls.add("write");
                        Reservation reservation =
                                (Reservation) args[0];
                        setId(reservation, ids.incrementAndGet());
                        return reservation;
                    }
                    throw unsupported(method);
                });
        AvailabilityService availability =
                new AvailabilityService(
                        null, null, null, clinicTime, properties,
                        Clock.fixed(NOW, ZoneOffset.UTC)) {
                    @Override
                    public List<com.amidog.app.scheduling.AvailabilityDtos.AvailableSlotResponse>
                    availableSlots(LocalDate from, LocalDate to) {
                        calls.add("availability");
                        return List.of(
                                new com.amidog.app.scheduling.AvailabilityDtos.AvailableSlotResponse(
                                        START, START.plusMinutes(30)));
                    }
                };
        OccupiedSlotReader occupied = new OccupiedSlotReader(null) {
            @Override
            public List<OccupiedSlot> findOverlapping(
                    Instant start, Instant end) {
                return List.of();
            }
        };
        ReservationCreationService reservationService =
                new ReservationCreationService(
                        new CurrentClient(clients),
                        reservations,
                        pets,
                        services,
                        availability,
                        clinicTime,
                        () -> calls.add("scheduling-lock"),
                        occupied,
                        properties,
                        Clock.fixed(NOW, ZoneOffset.UTC),
                        new RateLimitService(
                                Clock.fixed(NOW, ZoneOffset.UTC)),
                        notificationOperations(calls));
        AccountPrincipal principal = new AccountPrincipal(
                1L, 999L, "ana@example.com", "Ana",
                AccountType.CLIENT, "{noop}password", true, true);
        return new Fixture(
                reservationService, principal, calls);
    }

    private static CreateReservationRequest request() {
        return new CreateReservationRequest(
                START,
                List.of(new ReservationItemRequest(3L, 4L)),
                null);
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

    @SuppressWarnings("unchecked")
    private static <T> T proxy(
            Class<T> type, Invocation invocation) {
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

    private static NotificationOperations notificationOperations(
            List<String> calls) {
        return new NotificationOperations() {
            @Override
            public Notification onReservationCreated(
                    Reservation reservation) {
                calls.add("notification:new-reservation");
                return null;
            }

            @Override
            public Notification onClientCancelled(
                    Reservation reservation) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Notification onClientRescheduled(
                    Reservation reservation) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Notification onReservationConfirmed(
                    Reservation reservation) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Notification onAdminCancelled(
                    Reservation reservation) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Notification onAdminRescheduled(
                    Reservation reservation) {
                throw new UnsupportedOperationException();
            }
        };
    }

    private static void setId(Object entity, long id)
            throws Exception {
        Field field = entity.getClass().getDeclaredField("id");
        field.setAccessible(true);
        field.set(entity, id);
    }

    private record Fixture(
            ReservationCreationService service,
            AccountPrincipal principal,
            List<String> calls) {
    }

    @FunctionalInterface
    private interface Invocation {
        Object invoke(java.lang.reflect.Method method, Object[] args)
                throws Throwable;
    }
}
