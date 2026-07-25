package com.amidog.app.admin;

import com.amidog.app.auth.AccountPrincipal;
import com.amidog.app.reservation.ReservationLifecycleService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

import static com.amidog.app.admin.AdminDtos.AdminReservationDetail;
import static com.amidog.app.admin.AdminDtos.AdminReservationSummary;
import static com.amidog.app.reservation.ReservationLifecycleDtos.RescheduleRequest;
import static com.amidog.app.reservation.ReservationLifecycleDtos.RescheduleResponse;
import static com.amidog.app.reservation.ReservationLifecycleDtos.StatusChangeRequest;
import static com.amidog.app.reservation.ReservationLifecycleDtos.StatusChangeResponse;
import com.amidog.app.reservation.ReservationStatus;

@RestController
@RequestMapping("/api/v1/admin/reservations")
public class AdminReservationController {

    private final ReservationLifecycleService lifecycleService;
    private final AdminQueryService queries;

    public AdminReservationController(
            ReservationLifecycleService lifecycleService,
            AdminQueryService queries) {
        this.lifecycleService = lifecycleService;
        this.queries = queries;
    }

    @GetMapping
    PageResponse<AdminReservationSummary> reservations(
            @AuthenticationPrincipal AccountPrincipal principal,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate to,
            @RequestParam(required = false) ReservationStatus status,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return queries.reservations(
                principal, from, to, status, q, page, size);
    }

    @GetMapping("/{id}")
    AdminReservationDetail reservation(
            @AuthenticationPrincipal AccountPrincipal principal,
            @PathVariable long id) {
        return queries.reservation(principal, id);
    }

    @PatchMapping("/{id}/status")
    StatusChangeResponse changeStatus(
            @AuthenticationPrincipal AccountPrincipal principal,
            @PathVariable long id,
            @Valid @RequestBody StatusChangeRequest request) {
        return lifecycleService.changeStatusAdmin(
                principal, id, request);
    }

    @PatchMapping("/{id}/reschedule")
    RescheduleResponse reschedule(
            @AuthenticationPrincipal AccountPrincipal principal,
            @PathVariable long id,
            @Valid @RequestBody RescheduleRequest request) {
        return lifecycleService.rescheduleAdmin(
                principal, id, request);
    }
}
