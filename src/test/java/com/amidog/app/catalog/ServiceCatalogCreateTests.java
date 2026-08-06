package com.amidog.app.catalog;

import com.amidog.app.common.api.ConflictException;
import com.amidog.app.common.api.ConflictType;
import org.junit.jupiter.api.Test;
import org.postgresql.util.PSQLException;
import org.postgresql.util.ServerErrorMessage;
import org.springframework.dao.DataIntegrityViolationException;

import java.lang.reflect.Proxy;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ServiceCatalogCreateTests {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-07-29T12:00:00Z"), ZoneOffset.UTC);

    @Test
    void exactPostgresServiceCodeConstraintRaceBecomesStableConflict() {
        DataIntegrityViolationException failure =
                integrityViolation("23505", "services_code_key");

        assertThatThrownBy(() -> catalog(failure).create(request()))
                .isInstanceOf(ConflictException.class)
                .satisfies(exception -> assertThat(((ConflictException) exception).getType())
                        .isEqualTo(ConflictType.SERVICE_CODE_EXISTS))
                .hasMessage("Ya existe un servicio con ese código.")
                .hasMessageNotContaining("services_code_key");
    }

    @Test
    void wrongStateConstraintOrMissingServerMetadataIsRethrownUnchanged() {
        DataIntegrityViolationException wrongState =
                integrityViolation("23503", "services_code_key");
        DataIntegrityViolationException wrongConstraint =
                integrityViolation("23505", "services_name_key");
        DataIntegrityViolationException noServerMetadata =
                new DataIntegrityViolationException(
                        "message mentions services_code_key and 23505");

        assertSameFailure(wrongState);
        assertSameFailure(wrongConstraint);
        assertSameFailure(noServerMetadata);
    }

    @Test
    void matcherWalksWrappedCausesButNeverUsesMessageSubstrings() {
        DataIntegrityViolationException exact = new DataIntegrityViolationException(
                "outer", new IllegalStateException(
                "middle", integrityViolation("23505", "services_code_key").getCause()));
        DataIntegrityViolationException substring = new DataIntegrityViolationException(
                "23505 services_code_key",
                new IllegalStateException("services_code_key"));

        assertThat(ServiceCatalogService.isServiceCodeUniqueViolation(exact)).isTrue();
        assertThat(ServiceCatalogService.isServiceCodeUniqueViolation(substring)).isFalse();
    }

    private void assertSameFailure(DataIntegrityViolationException failure) {
        assertThatThrownBy(() -> catalog(failure).create(request()))
                .isSameAs(failure);
    }

    private ServiceCatalogService catalog(DataIntegrityViolationException failure) {
        ServiceOfferingRepository repository =
                (ServiceOfferingRepository) Proxy.newProxyInstance(
                        getClass().getClassLoader(),
                        new Class<?>[]{ServiceOfferingRepository.class},
                        (proxy, method, args) -> switch (method.getName()) {
                            case "existsByCode" -> false;
                            case "saveAndFlush" -> throw failure;
                            case "toString" -> "FailingServiceRepository";
                            default -> throw new UnsupportedOperationException(method.getName());
                        });
        return new ServiceCatalogService(
                repository, new ServiceCodeNormalizer(), CLOCK);
    }

    private ServiceDtos.ServiceCreateRequest request() {
        return new ServiceDtos.ServiceCreateRequest(
                "consulta", "Consulta", null, 0);
    }

    private DataIntegrityViolationException integrityViolation(
            String sqlState,
            String constraint) {
        ServerErrorMessage serverError = new ServerErrorMessage("") {
            @Override
            public String getSQLState() {
                return sqlState;
            }

            @Override
            public String getConstraint() {
                return constraint;
            }
        };
        PSQLException postgres = new PSQLException(serverError);
        return new DataIntegrityViolationException("sanitized wrapper", postgres);
    }
}
