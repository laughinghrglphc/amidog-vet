package com.amidog.app.reservation;

import com.amidog.app.catalog.ServiceOffering;
import com.amidog.app.client.Pet;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Objects;

@Entity
@Table(name = "reservation_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReservationItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reservation_id", nullable = false)
    private Reservation reservation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pet_id", nullable = false)
    private Pet pet;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_id", nullable = false)
    private ServiceOffering service;

    @Column(name = "service_name_snapshot", nullable = false, length = 100)
    private String serviceNameSnapshot;

    static ReservationItem create(
            Reservation reservation, Pet pet, ServiceOffering service) {
        return new ReservationItem(reservation, pet, service);
    }

    private ReservationItem(
            Reservation reservation, Pet pet, ServiceOffering service) {
        this.reservation =
                Objects.requireNonNull(reservation, "reservation");
        this.pet = Objects.requireNonNull(pet, "pet");
        this.service = Objects.requireNonNull(service, "service");
        this.serviceNameSnapshot = service.getName();
    }
}
