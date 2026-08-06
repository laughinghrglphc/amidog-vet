package com.amidog.app.client;

import com.amidog.app.auth.AccountPrincipal;
import com.amidog.app.auth.AccountType;
import com.amidog.app.auth.UserAccount;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class PetServiceLockOrderTests {

    @Test
    void archiveLocksOwnedActivePetBeforeReservationGuardAndStateChange() throws Exception {
        List<String> calls = new ArrayList<>();
        Instant now = Instant.parse("2026-07-29T15:00:00Z");
        UserAccount account = UserAccount.client("ana@example.com", "{noop}password", now);
        Client client = Client.create(account, "Ana", "+56911111111", now);
        Pet pet = Pet.create(client, "Milo", "Gato", null, null, now);
        setId(account, 11L);
        setId(client, 22L);
        setId(pet, 33L);

        ClientRepository clients = proxy(ClientRepository.class, (method, args) -> {
            if (method.getName().equals("findByUserIdAndActiveTrue")) {
                return Optional.of(client);
            }
            throw new UnsupportedOperationException(method.getName());
        });
        PetRepository pets = proxy(PetRepository.class, (method, args) -> {
            if (method.getName().equals("findActiveOwnedForUpdate")) {
                calls.add("lock");
                assertThat(args).containsExactly(33L, 22L);
                return Optional.of(pet);
            }
            if (method.getName().equals("saveAndFlush")) {
                calls.add("save");
                return pet;
            }
            throw new UnsupportedOperationException(method.getName());
        });
        PetReservationGuard guard = new PetReservationGuard(null) {
            @Override
            public boolean hasFutureOccupied(long petId, Instant checkedAt) {
                calls.add("guard");
                assertThat(petId).isEqualTo(33L);
                assertThat(checkedAt).isEqualTo(now);
                return false;
            }
        };
        PetService service = new PetService(
                new CurrentClient(clients), pets, guard, Clock.fixed(now, ZoneOffset.UTC));

        service.archive(
                new AccountPrincipal(11L, 999L, "ana@example.com", "Ana",
                        AccountType.CLIENT, "{noop}password", true, true),
                33L);

        assertThat(calls).containsExactly("lock", "guard", "save");
        assertThat(pet.isActive()).isFalse();
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, Invocation invocation) {
        return (T) Proxy.newProxyInstance(
                type.getClassLoader(), new Class<?>[]{type},
                (proxy, method, args) -> {
                    if (method.getDeclaringClass() == Object.class) {
                        return switch (method.getName()) {
                            case "toString" -> type.getSimpleName() + "Proxy";
                            case "hashCode" -> System.identityHashCode(proxy);
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

    @FunctionalInterface
    private interface Invocation {
        Object invoke(java.lang.reflect.Method method, Object[] args) throws Throwable;
    }
}
