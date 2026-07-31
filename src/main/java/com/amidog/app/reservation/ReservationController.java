package com.amidog.app.reservation;

import com.amidog.app.auth.AccountPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

import static com.amidog.app.reservation.ReservationDtos.CreateReservationRequest;
import static com.amidog.app.reservation.ReservationDtos.ReservationResponse;
import static com.amidog.app.reservation.ReservationLifecycleDtos.CancelReservationRequest;
import static com.amidog.app.reservation.ReservationLifecycleDtos.CancellationResponse;
import static com.amidog.app.reservation.ReservationLifecycleDtos.RescheduleRequest;
import static com.amidog.app.reservation.ReservationLifecycleDtos.RescheduleResponse;

@RestController
@RequestMapping("/api/v1/me/reservations")
public class ReservationController {

    private final ReservationCreationService reservationService;
    private final ReservationLifecycleService lifecycleService;

    public ReservationController(
            ReservationCreationService reservationService,
            ReservationLifecycleService lifecycleService) {
        this.reservationService = reservationService;
        this.lifecycleService = lifecycleService;
    }

    @GetMapping
    List<ReservationResponse> list(
            @AuthenticationPrincipal AccountPrincipal principal) {
        return reservationService.list(principal);
    }

    @PostMapping
    ResponseEntity<ReservationResponse> create(
            @AuthenticationPrincipal AccountPrincipal principal,
            @Valid @RequestBody CreateReservationRequest request) {
        ReservationResponse response =
                reservationService.create(principal, request);
        return ResponseEntity.created(
                        URI.create(
                                "/api/v1/me/reservations/" + response.id()))
                .body(response);
    }

    @PatchMapping("/{id}/cancel")
    CancellationResponse cancel(
            @AuthenticationPrincipal AccountPrincipal principal,
            @PathVariable long id,
            @Valid @RequestBody CancelReservationRequest request) {
        return lifecycleService.cancelClient(
                principal, id, request);
    }

    @PatchMapping("/{id}/reschedule")
    RescheduleResponse reschedule(
            @AuthenticationPrincipal AccountPrincipal principal,
            @PathVariable long id,
            @Valid @RequestBody RescheduleRequest request) {
        return lifecycleService.rescheduleClient(
                principal, id, request);
    }
}
