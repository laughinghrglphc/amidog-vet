package com.amidog.app.models;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "Procedure_Records")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ProcedureRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_record")
    private Integer idRecord;

    @ManyToOne
    @JoinColumn(name = "id_procedure", nullable = false)
    private Procedure procedure;

    @ManyToOne
    @JoinColumn(name = "id_consultation", nullable = false)
    private Consultation consultation;
}
