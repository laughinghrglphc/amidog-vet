package com.amidog.app.models;

import jakarta.persistence.*;
import lombok.*;

import java.util.List;

@Entity
@Table(name = "Clients")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Client {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_client")
    private Integer idClient;

    @Column(nullable = false)
    private String name;

    @Column(unique = true)
    private String email;

    private String phone;

    // Useful for getting a list of the carts of the client
    @OneToMany(mappedBy = "client")
    private List<Cart> carts;

    // Useful for a list of reservations of the client
    @OneToMany(mappedBy = "client")
    private List<Reservation> reservations;
}
