package com.amidog.app.models;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.List;

@Entity
@Table(name = "Procedures")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Procedure {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_procedure")
    private Integer idProcedure;

    @Column(name = "procedure_type")
    private String procedureType;

    @Column(precision = 10, scale = 2)
    private BigDecimal cost;

    @OneToMany(mappedBy = "procedure")
    private List<ProcedureRecord> procedureRecords;
}
