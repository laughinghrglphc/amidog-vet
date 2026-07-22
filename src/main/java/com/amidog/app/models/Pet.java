package com.amidog.app.models;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.List;

@Entity
@Table(name = "Pets")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Pet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_pet")
    private Integer idPet;

    @Column(nullable = false)
    private String name;

    private String species;

    private LocalDate birthdate;

    private String breed;

    @OneToMany(mappedBy = "pet")
    private List<Consultation> consultations;
}
