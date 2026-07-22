package com.amidog.app.models;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "Purchases")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Purchase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_purchase")
    private Integer idPurchase;

    @ManyToOne
    @JoinColumn(name = "id_cart", nullable = false)
    private Cart cart;

    @ManyToOne
    @JoinColumn(name = "id_product", nullable = false)
    private Product product;

    @Column(name = "product_quantity")
    private Integer productQuantity;
}
