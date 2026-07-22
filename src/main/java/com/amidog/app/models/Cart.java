package com.amidog.app.models;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.List;

@Entity
@Table(name = "Carts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Cart {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_cart")
    private Integer idCart;

    @ManyToOne
    @JoinColumn(name = "id_client", nullable = false)
    private Client client;

    private LocalDate date;

    @OneToMany(mappedBy = "cart")
    private List<Purchase> purchases;
}
