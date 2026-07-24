package com.amidog.app.admin;

import com.amidog.app.auth.AccountPrincipal;
import com.amidog.app.auth.AccountType;
import com.amidog.app.auth.UserAccount;
import com.amidog.app.client.Client;
import com.amidog.app.client.ClientRepository;
import com.amidog.app.client.Pet;
import com.amidog.app.client.PetRepository;
import com.amidog.app.common.api.ConflictException;
import com.amidog.app.common.api.ConflictType;
import com.amidog.app.config.AmidogProperties;
import com.amidog.app.scheduling.ClinicTime;
import org.junit.jupiter.api.Test;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AdminManagementServiceTests {

    private static final Instant CREATED =
            Instant.parse("2026-07-01T12:00:00Z");
    private static final Instant NOW =
            Instant.parse("2026-07-29T12:00:00Z");

    @Test
    void clientUpdateTrimsFieldsAndCanDeactivateWithoutDeletingIdentity() throws Exception {
        Client client = client();
        ClientRepository clients = proxy(
                ClientRepository.class,
                (method, args) -> switch (method.getName()) {
                    case "findById" -> Optional.of(client);
                    case "saveAndFlush" -> client;
                    default -> throw new UnsupportedOperationException(
                            method.getName());
                });
        AdminManagementService service = new AdminManagementService(
                clients,
                proxy(PetRepository.class, unsupported()),
                clinicTime(),
                fixedClock());

        var response = service.updateClient(
                admin(),
                22L,
                new AdminDtos.AdminClientUpdateRequest(
                        "  Ana P\u00e9rez  ",
                        "  +56922222222  ",
                        false));

        assertThat(response.id()).isEqualTo(22L);
        assertThat(response.name()).isEqualTo("Ana P\u00e9rez");
        assertThat(response.phone()).isEqualTo("+56922222222");
        assertThat(response.active()).isFalse();
        assertThat(client.getUser().getEmailNormalized())
                .isEqualTo("ana@example.com");
        assertThat(client.getUpdatedAt()).isEqualTo(NOW);
    }

    @Test
    void petFullFormUpdateCanClearOptionalFieldsButCannotChangeOwner() throws Exception {
        Client owner = client();
        Pet pet = Pet.create(
                owner, "Luna", "Perro", "Mestiza",
                LocalDate.of(2020, 1, 2), CREATED);
        setId(pet, 33L);
        PetRepository pets = proxy(
                PetRepository.class,
                (method, args) -> switch (method.getName()) {
                    case "findById" -> Optional.of(pet);
                    case "saveAndFlush" -> pet;
                    default -> throw new UnsupportedOperationException(
                            method.getName());
                });
        AdminManagementService service = new AdminManagementService(
                proxy(ClientRepository.class, unsupported()),
                pets,
                clinicTime(),
                fixedClock());

        var response = service.updatePet(
                admin(),
                33L,
                new AdminDtos.AdminPetUpdateRequest(
                        "  Lunita ", " Canina ", " ", null));

        assertThat(response.id()).isEqualTo(33L);
        assertThat(response.clientId()).isEqualTo(22L);
        assertThat(response.ownerName()).isEqualTo("Ana");
        assertThat(response.name()).isEqualTo("Lunita");
        assertThat(response.species()).isEqualTo("Canina");
        assertThat(response.breed()).isNull();
        assertThat(response.birthdate()).isNull();
        assertThat(pet.getClient()).isSameAs(owner);
    }

    @Test
    void optimisticFailuresMapToTheAcceptedClosedConflicts() throws Exception {
        Client client = client();
        Pet pet = Pet.create(
                client, "Luna", "Perro", null, null, CREATED);
        setId(pet, 33L);
        var conflict = new ObjectOptimisticLockingFailureException(
                Client.class, 22L);
        ClientRepository clients = proxy(
                ClientRepository.class,
                (method, args) -> switch (method.getName()) {
                    case "findById" -> Optional.of(client);
                    case "saveAndFlush" -> throw conflict;
                    default -> throw new UnsupportedOperationException(
                            method.getName());
                });
        PetRepository pets = proxy(
                PetRepository.class,
                (method, args) -> switch (method.getName()) {
                    case "findById" -> Optional.of(pet);
                    case "saveAndFlush" -> throw conflict;
                    default -> throw new UnsupportedOperationException(
                            method.getName());
                });
        AdminManagementService service =
                new AdminManagementService(
                        clients, pets, clinicTime(), fixedClock());

        assertThatThrownBy(() -> service.updateClient(
                admin(), 22L,
                new AdminDtos.AdminClientUpdateRequest(
                        "Ana", "+56911111111", true)))
                .isInstanceOf(ConflictException.class)
                .extracting("type")
                .isEqualTo(ConflictType.CLIENT_CONCURRENT_UPDATE);
        assertThatThrownBy(() -> service.updatePet(
                admin(), 33L,
                new AdminDtos.AdminPetUpdateRequest(
                        "Luna", "Perro", null, null)))
                .isInstanceOf(ConflictException.class)
                .extracting("type")
                .isEqualTo(ConflictType.PET_CONCURRENT_UPDATE);
    }

    private static Client client() throws Exception {
        UserAccount account = UserAccount.client(
                "ana@example.com", "{noop}secret", CREATED);
        Client client = Client.create(
                account, "Ana", "+56911111111", CREATED);
        setId(account, 11L);
        setId(client, 22L);
        return client;
    }

    private static AccountPrincipal admin() {
        return new AccountPrincipal(
                1L, null, "admin@amidog.cl", "Admin",
                AccountType.ADMIN, "{noop}secret", true, true);
    }

    private static Clock fixedClock() {
        return Clock.fixed(NOW, ZoneOffset.UTC);
    }

    private static ClinicTime clinicTime() {
        return new ClinicTime(new AmidogProperties(
                ZoneOffset.of("-04:00"),
                URI.create("http://localhost"),
                URI.create("http://localhost"),
                new AmidogProperties.Booking(false, 30, 2, 90),
                new AmidogProperties.Admin("", "", "Admin", ""),
                new AmidogProperties.Contact("", ""),
                new AmidogProperties.Email("log")));
    }

    private static Invocation unsupported() {
        return (method, args) -> {
            throw new UnsupportedOperationException(method.getName());
        };
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

    private static void setId(Object target, long id)
            throws ReflectiveOperationException {
        Field field = target.getClass().getDeclaredField("id");
        field.setAccessible(true);
        field.set(target, id);
    }

    @FunctionalInterface
    private interface Invocation {
        Object invoke(
                java.lang.reflect.Method method,
                Object[] arguments) throws Throwable;
    }
}
