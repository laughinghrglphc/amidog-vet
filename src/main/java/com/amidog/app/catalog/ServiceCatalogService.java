package com.amidog.app.catalog;

import com.amidog.app.common.api.ConflictException;
import com.amidog.app.common.api.ConflictType;
import com.amidog.app.common.api.NotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.postgresql.util.PSQLException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

import static com.amidog.app.catalog.ServiceDtos.ServiceCreateRequest;
import static com.amidog.app.catalog.ServiceDtos.ServiceResponse;
import static com.amidog.app.catalog.ServiceDtos.ServiceUpdateRequest;

@Service
public class ServiceCatalogService {

    static final String NOT_FOUND = "No se encontró el servicio.";
    private static final String UNIQUE_VIOLATION = "23505";
    private static final String SERVICE_CODE_CONSTRAINT = "services_code_key";

    private final ServiceOfferingRepository services;
    private final ServiceCodeNormalizer codes;
    private final Clock clock;

    ServiceCatalogService(ServiceOfferingRepository services, ServiceCodeNormalizer codes, Clock clock) {
        this.services = services;
        this.codes = codes;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<ServiceResponse> publicCatalog() {
        return services.findAllByActiveTrueOrderByDisplayOrderAscNameAscCodeAsc()
                .stream().map(ServiceResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<ServiceResponse> adminCatalog() {
        return services.findAllByOrderByDisplayOrderAscNameAscCodeAsc()
                .stream().map(ServiceResponse::from).toList();
    }

    @Transactional
    public ServiceResponse create(ServiceCreateRequest request) {
        String code = codes.normalize(request.code());
        if (services.existsByCode(code)) {
            throw codeExists();
        }
        try {
            ServiceOffering service = ServiceOffering.create(
                    code, request.name(), request.description(), request.displayOrder(), clock.instant());
            return ServiceResponse.from(services.saveAndFlush(service));
        } catch (DataIntegrityViolationException exception) {
            if (isServiceCodeUniqueViolation(exception)) {
                throw codeExists();
            }
            throw exception;
        }
    }

    @Transactional
    public ServiceResponse update(long id, ServiceUpdateRequest request) {
        ServiceOffering service = services.findByIdForUpdate(id)
                .orElseThrow(() -> new NotFoundException(NOT_FOUND));
        try {
            service.update(
                    request.name(), request.description(), request.displayOrder(), request.active(), clock.instant());
            return ServiceResponse.from(services.saveAndFlush(service));
        } catch (ObjectOptimisticLockingFailureException exception) {
            throw new ConflictException(ConflictType.SERVICE_CONCURRENT_UPDATE);
        }
    }

    @Transactional
    public void archive(long id) {
        ServiceOffering service = services.findByIdForUpdate(id)
                .orElseThrow(() -> new NotFoundException(NOT_FOUND));
        service.archive(clock.instant());
        services.saveAndFlush(service);
    }

    private ConflictException codeExists() {
        return new ConflictException(ConflictType.SERVICE_CODE_EXISTS);
    }

    static boolean isServiceCodeUniqueViolation(Throwable failure) {
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        Throwable current = failure;
        while (current != null && seen.add(current)) {
            if (current instanceof PSQLException postgres
                    && UNIQUE_VIOLATION.equals(postgres.getSQLState())
                    && postgres.getServerErrorMessage() != null
                    && SERVICE_CODE_CONSTRAINT.equals(
                            postgres.getServerErrorMessage().getConstraint())) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
