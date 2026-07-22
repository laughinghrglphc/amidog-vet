package com.amidog.app.controllers;

import com.amidog.app.dtos.ReservationRequestDTO;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/reservation")
public class CreateReservationController {

    public ResponseEntity<Void> createReservation(@RequestBody ReservationRequestDTO reservationRequest) {
        return null;
    }

}
