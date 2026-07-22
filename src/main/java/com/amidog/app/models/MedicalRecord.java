package com.amidog.app.models;

import jakarta.persistence.*;
import lombok.*;

import java.util.List;

@Entity
@Table(name = "Medical_Records")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MedicalRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_medical_record")
    private Integer idMedicalRecord;

    // Maybe the size information type needs to be more nuance?
    private String size;

    private Double weight;

    @Column(columnDefinition = "TEXT")
    private String diagnosis;

    @Column(name = "medical_history", columnDefinition = "TEXT")
    private String medicalHistory;

    @OneToMany(mappedBy = "medicalRecord")
    private List<Consultation> consultations;
}
