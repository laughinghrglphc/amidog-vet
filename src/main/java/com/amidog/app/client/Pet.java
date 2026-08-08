package com.amidog.app.client;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "pets")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Pet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "client_id", nullable = false)
    private Client client;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(nullable = false, length = 40)
    private String species;

    @Column(length = 80)
    private String breed;

    private LocalDate birthdate;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    public static Pet create(
            Client client, String name, String species, String breed, LocalDate birthdate, Instant now
    ) {
        return new Pet(client, name, species, breed, birthdate, now);
    }

    private Pet(Client client, String name, String species, String breed, LocalDate birthdate, Instant now) {
        this.client = client;
        this.name = requireTrimmed(name, "name");
        this.species = requireTrimmed(species, "species");
        this.breed = trimToNull(breed);
        this.birthdate = birthdate;
        this.active = true;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void update(String name, String species, String breed, LocalDate birthdate, Instant now) {
        this.name = requireTrimmed(name, "name");
        this.species = requireTrimmed(species, "species");
        this.breed = trimToNull(breed);
        this.birthdate = birthdate;
        this.updatedAt = now;
    }

    public void archive(Instant now) {
        if (active) {
            active = false;
            updatedAt = now;
        }
    }

    private static String requireTrimmed(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }

    private static String trimToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }
}
