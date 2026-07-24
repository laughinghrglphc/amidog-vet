package com.amidog.app.admin;

import com.amidog.app.auth.AccountPrincipal;
import com.amidog.app.auth.AccountType;
import com.amidog.app.common.api.NotFoundException;
import com.amidog.app.reservation.ReservationStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static com.amidog.app.admin.AdminDtos.AdminClientDetail;
import static com.amidog.app.admin.AdminDtos.AdminClientSummary;
import static com.amidog.app.admin.AdminDtos.AdminPetDetail;
import static com.amidog.app.admin.AdminDtos.AdminPetSummary;
import static com.amidog.app.admin.AdminDtos.AdminReservationDetail;
import static com.amidog.app.admin.AdminDtos.AdminReservationSummary;

@Service
public class AdminQueryService {

    private static final String RESERVATION_NOT_FOUND =
            "No se encontr\u00f3 la reserva.";
    private static final String CLIENT_NOT_FOUND =
            "No se encontr\u00f3 el cliente.";
    private static final String PET_NOT_FOUND =
            "No se encontr\u00f3 la mascota.";

    private final AdminReadRepository reads;
    private final AdminRequestValidator validator;

    public AdminQueryService(
            AdminReadRepository reads,
            AdminRequestValidator validator) {
        this.reads = reads;
        this.validator = validator;
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminReservationSummary> reservations(
            AccountPrincipal principal,
            LocalDate from,
            LocalDate to,
            ReservationStatus status,
            String search,
            int page,
            int size) {
        requireAdministrator(principal);
        var pagination = validator.page(page, size);
        var range = validator.dateRange(from, to).orElse(null);
        String searchPattern = validator.search(search)
                .map(AdminRequestValidator.SearchTerm::likePattern)
                .orElse(null);
        return reads.reservations(
                new AdminReadRepository.ReservationQuery(
                        range == null ? null : range.startInclusive(),
                        range == null ? null : range.endExclusive(),
                        status,
                        searchPattern,
                        pagination.page(),
                        pagination.size()));
    }

    @Transactional(readOnly = true)
    public AdminReservationDetail reservation(
            AccountPrincipal principal,
            long id) {
        requireAdministrator(principal);
        return reads.reservation(id)
                .orElseThrow(() ->
                        new NotFoundException(RESERVATION_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminClientSummary> clients(
            AccountPrincipal principal,
            String search,
            Boolean active,
            int page,
            int size) {
        requireAdministrator(principal);
        var pagination = validator.page(page, size);
        String searchPattern = validator.search(search)
                .map(AdminRequestValidator.SearchTerm::likePattern)
                .orElse(null);
        return reads.clients(new AdminReadRepository.ClientQuery(
                searchPattern,
                active,
                pagination.page(),
                pagination.size()));
    }

    @Transactional(readOnly = true)
    public AdminClientDetail client(
            AccountPrincipal principal,
            long id) {
        requireAdministrator(principal);
        return reads.client(id)
                .orElseThrow(() ->
                        new NotFoundException(CLIENT_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminPetSummary> pets(
            AccountPrincipal principal,
            String search,
            Boolean active,
            Long clientId,
            int page,
            int size) {
        requireAdministrator(principal);
        var pagination = validator.page(page, size);
        String searchPattern = validator.search(search)
                .map(AdminRequestValidator.SearchTerm::likePattern)
                .orElse(null);
        return reads.pets(new AdminReadRepository.PetQuery(
                searchPattern,
                active,
                validator.optionalId(clientId),
                pagination.page(),
                pagination.size()));
    }

    @Transactional(readOnly = true)
    public AdminPetDetail pet(
            AccountPrincipal principal,
            long id) {
        requireAdministrator(principal);
        return reads.pet(id)
                .orElseThrow(() ->
                        new NotFoundException(PET_NOT_FOUND));
    }

    private static void requireAdministrator(
            AccountPrincipal principal) {
        if (principal == null
                || principal.getAccountType() != AccountType.ADMIN
                || !principal.isEnabled()) {
            throw new AccessDeniedException(
                    "Administrator required");
        }
    }
}
