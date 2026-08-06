package com.amidog.app.reservation;

import com.amidog.app.AppApplication;
import org.junit.jupiter.api.Test;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class ReservationPersistenceDiscoveryTests {

    @Test
    void applicationScansEveryAcceptedPersistenceModule() {
        EntityScan entities =
                AppApplication.class.getAnnotation(EntityScan.class);
        EnableJpaRepositories repositories =
                AppApplication.class.getAnnotation(
                        EnableJpaRepositories.class);

        assertThat(packages(entities.basePackageClasses()))
                .contains(
                        "com.amidog.app.auth",
                        "com.amidog.app.client",
                        "com.amidog.app.catalog",
                        "com.amidog.app.scheduling",
                        "com.amidog.app.reservation");
        assertThat(packages(repositories.basePackageClasses()))
                .contains(
                        "com.amidog.app.auth",
                        "com.amidog.app.client",
                        "com.amidog.app.catalog",
                        "com.amidog.app.scheduling",
                        "com.amidog.app.reservation");
    }

    private static Set<String> packages(Class<?>[] anchors) {
        return Arrays.stream(anchors)
                .map(type -> type.getPackageName())
                .collect(Collectors.toSet());
    }
}
