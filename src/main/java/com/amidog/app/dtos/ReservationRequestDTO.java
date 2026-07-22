package com.amidog.app.dto;

import java.time.LocalDate;
import java.time.LocalTime;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class ReservationRequestDTO {
    private String petName;
    private String petType;
    private LocalDate reservationDate;
    private LocalTime reservationTime;
    private String clientName;
    private String clientPhone;
    private String clientEmail;
}

