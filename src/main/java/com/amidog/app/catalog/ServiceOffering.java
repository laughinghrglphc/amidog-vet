package com.amidog.app.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "services")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ServiceOffering {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 60)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 500)
    private String description;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    public static ServiceOffering create(
            String code, String name, String description, int displayOrder, Instant now) {
        return new ServiceOffering(code, name, description, displayOrder, now);
    }

    private ServiceOffering(String code, String name, String description, int displayOrder, Instant now) {
        this.code = code;
        updateFields(name, description, displayOrder, true, now);
        this.createdAt = now;
    }

    public void update(String name, String description, int displayOrder, boolean active, Instant now) {
        updateFields(name, description, displayOrder, active, now);
    }

    public void archive(Instant now) {
        if (active) {
            active = false;
            updatedAt = now;
        }
    }

    private void updateFields(String name, String description, int displayOrder, boolean active, Instant now) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (displayOrder < 0) {
            throw new IllegalArgumentException("displayOrder must not be negative");
        }
        this.name = name.trim();
        this.description = description == null || description.trim().isEmpty() ? null : description.trim();
        this.displayOrder = displayOrder;
        this.active = active;
        this.updatedAt = now;
    }
}
