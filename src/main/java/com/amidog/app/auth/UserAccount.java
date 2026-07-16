package com.amidog.app.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Locale;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "email_normalized", nullable = false, unique = true, length = 254)
    private String emailNormalized;

    @Column(name = "password_hash", length = 255)
    private String passwordHash;

    @Column(name = "email_verified_at")
    private Instant emailVerifiedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false, length = 20)
    private AccountType accountType;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    public static UserAccount client(String email, String passwordHash, Instant now) {
        return new UserAccount(email, passwordHash, AccountType.CLIENT, false, now);
    }

    public static UserAccount admin(String email, String passwordHash, Instant now) {
        UserAccount account = new UserAccount(email, passwordHash, AccountType.ADMIN, true, now);
        account.emailVerifiedAt = now;
        return account;
    }

    private UserAccount(String email, String hash, AccountType type, boolean enabled, Instant now) {
        this.emailNormalized = normalizeEmail(email);
        this.passwordHash = hash;
        this.accountType = type;
        this.enabled = enabled;
        this.createdAt = now;
        this.updatedAt = now;
    }

    private static String normalizeEmail(String email) {
        if (email == null) {
            throw new IllegalArgumentException("Email must not be null");
        }

        String normalized = email.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Email must not be blank");
        }

        return normalized;
    }

    public void verify(Instant now) {
        emailVerifiedAt = now;
        enabled = true;
        updatedAt = now;
    }

    public void replacePassword(String hash, Instant now) {
        passwordHash = hash;
        updatedAt = now;
    }

    public boolean isVerified() {
        return emailVerifiedAt != null;
    }
}
