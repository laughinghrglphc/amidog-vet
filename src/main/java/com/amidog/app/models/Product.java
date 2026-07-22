package com.amidog.app.models;

import jakarta.persistence.*;
import lombok.*;

import java.util.List;

@Entity
@Table(name = "Products")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_product")
    private Integer idProduct;

    @Column(name = "product_name", nullable = false)
    private String productName;

    @Column(name = "product_type")
    private String productType;

    private Long cost;

    @OneToMany(mappedBy = "product")
    private List<Purchase> purchases;
}
