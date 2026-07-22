package com.amidog.app.models;

import jakarta.persistence.*;
import lombok.*;

import java.util.List;

@Entity
@Table(name = "Consultations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Consultation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_consultation")
    private Integer idConsultation;

    @ManyToOne
    @JoinColumn(name = "id_pet", nullable = false)
    private Pet pet;

    @ManyToOne
    @JoinColumn(name = "id_reservation", nullable = false)
    private Reservation reservation;

    @ManyToOne
    @JoinColumn(name = "id_medical_record", nullable = false)
    private MedicalRecord medicalRecord;

    @OneToMany(mappedBy = "consultation")
    private List<ProcedureRecord> procedureRecords;
}
