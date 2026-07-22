package com.amidog.app.dtos;

import java.time.LocalDate;
import java.time.LocalTime;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class ReservationRequestDTO {
    private LocalDate reservationDate;
    private LocalTime reservationTime;
    private String clientName;
    private String clientPhone;
    private String clientEmail;
    private List<PetInformation> petsOfRequest;
}

