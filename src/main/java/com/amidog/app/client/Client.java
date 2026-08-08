package com.amidog.app.client;

import com.amidog.app.auth.UserAccount;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "clients")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Client {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private UserAccount user;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 30)
    private String phone;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    public static Client create(UserAccount user, String name, String phone, Instant now) {
        return new Client(user, name, phone, now);
    }

    private Client(UserAccount user, String name, String phone, Instant now) {
        this.user = user;
        this.name = requireTrimmed(name, "name");
        this.phone = requireTrimmed(phone, "phone");
        this.active = true;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void updateProfile(String name, String phone, Instant now) {
        this.name = requireTrimmed(name, "name");
        this.phone = requireTrimmed(phone, "phone");
        this.updatedAt = now;
    }

    public void updateByAdministrator(
            String name,
            String phone,
            boolean active,
            Instant now) {
        this.name = requireTrimmed(name, "name");
        this.phone = requireTrimmed(phone, "phone");
        this.active = active;
        this.updatedAt = now;
    }

    private static String requireTrimmed(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
