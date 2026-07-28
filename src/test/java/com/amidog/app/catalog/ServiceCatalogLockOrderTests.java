package com.amidog.app.catalog;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.amidog.app.catalog.ServiceDtos.ServiceUpdateRequest;
import static org.assertj.core.api.Assertions.assertThat;

class ServiceCatalogLockOrderTests {

    @Test
    void mutationUsesSharedPessimisticLockRepositoryMethod() throws Exception {
        List<String> calls = new ArrayList<>();
        Instant now = Instant.parse("2026-07-29T15:00:00Z");
        ServiceOffering entity = ServiceOffering.create(
                "consulta", "Consulta", null, 0, now.minusSeconds(60));
        Field id = ServiceOffering.class.getDeclaredField("id");
        id.setAccessible(true);
        id.set(entity, 8L);

        ServiceOfferingRepository repository = (ServiceOfferingRepository) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{ServiceOfferingRepository.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("findByIdForUpdate")) {
                        calls.add("lock");
                        return Optional.of(entity);
                    }
                    if (method.getName().equals("saveAndFlush")) {
                        calls.add("save");
                        return entity;
                    }
                    if (method.getDeclaringClass() == Object.class) {
                        return switch (method.getName()) {
                            case "toString" -> "repository";
                            case "hashCode" -> System.identityHashCode(proxy);
                            case "equals" -> proxy == args[0];
                            default -> null;
                        };
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
        ServiceCatalogService catalog = new ServiceCatalogService(
                repository, new ServiceCodeNormalizer(), Clock.fixed(now, ZoneOffset.UTC));

        var response = catalog.update(
                8L, new ServiceUpdateRequest(" Nueva ", " ", 2, true));

        assertThat(calls).containsExactly("lock", "save");
        assertThat(response.code()).isEqualTo("consulta");
        assertThat(response.name()).isEqualTo("Nueva");
        assertThat(response.description()).isNull();
    }
}
