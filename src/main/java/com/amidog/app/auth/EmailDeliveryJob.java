package com.amidog.app.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

@Entity
@Table(name = "email_delivery_jobs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EmailDeliveryJob {

    private static final Duration LEASE_DURATION = Duration.ofMinutes(1);
    private static final Pattern SHA_256_HEX = Pattern.compile("[0-9a-f]{64}");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private UserAccount user;

    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_type", nullable = false)
    private EmailDeliveryType deliveryType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private State state;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "lease_until")
    private Instant leaseUntil;

    @Column(name = "delivery_fence", nullable = false, length = 64)
    private String deliveryFence;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "token_hash", length = 64, columnDefinition = "char(64)")
    private String tokenHash;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    enum State {
        PENDING,
        PROCESSING,
        COMPLETED
    }

    static EmailDeliveryJob create(UserAccount user, EmailDeliveryType type, String tokenHash, Instant now) {
        return new EmailDeliveryJob(
                Objects.requireNonNull(user, "user"),
                Objects.requireNonNull(type, "type"),
                requireValidTokenHash(tokenHash),
                Objects.requireNonNull(now, "now"));
    }

    private EmailDeliveryJob(UserAccount user, EmailDeliveryType type, String tokenHash, Instant now) {
        this.user = user;
        this.deliveryType = type;
        this.tokenHash = tokenHash;
        this.state = State.PROCESSING;
        this.nextAttemptAt = now;
        this.leaseUntil = now.plus(LEASE_DURATION);
        this.deliveryFence = newFence();
        this.createdAt = now;
        this.updatedAt = now;
    }

    boolean complete(String fence, Instant now) {
        if (!owns(fence, now)) {
            return false;
        }
        state = State.COMPLETED;
        completedAt = now;
        leaseUntil = null;
        updatedAt = now;
        return true;
    }

    boolean retry(String fence, Instant now) {
        if (!owns(fence, now)) {
            return false;
        }
        state = State.PENDING;
        attempts++;
        nextAttemptAt = now.plusSeconds(Math.min(3_600, 30L << Math.min(6, attempts)));
        leaseUntil = null;
        updatedAt = now;
        return true;
    }

    boolean claim(Instant now) {
        if (state == State.COMPLETED
                || !isValidTokenHash(tokenHash)
                || (state == State.PROCESSING && leaseUntil != null && leaseUntil.isAfter(now))) {
            return false;
        }
        state = State.PROCESSING;
        leaseUntil = now.plus(LEASE_DURATION);
        deliveryFence = newFence();
        updatedAt = now;
        return true;
    }

    boolean owns(String fence, Instant now) {
        return state == State.PROCESSING
                && isValidTokenHash(tokenHash)
                && deliveryFence.equals(fence)
                && leaseUntil != null
                && leaseUntil.isAfter(now);
    }

    boolean represents(DeliveryAttempt attempt) {
        return user.getId().equals(attempt.userId())
                && deliveryType == attempt.deliveryType()
                && deliveryFence.equals(attempt.fence())
                && Objects.equals(tokenHash, attempt.tokenHash());
    }

    boolean replaceTokenHash(String fence, String expectedHash, String replacementHash, Instant now) {
        requireValidTokenHash(expectedHash);
        requireValidTokenHash(replacementHash);
        if (!owns(fence, now) || !Objects.equals(tokenHash, expectedHash)) {
            return false;
        }
        tokenHash = replacementHash;
        updatedAt = now;
        return true;
    }

    private static String newFence() {
        return UUID.randomUUID().toString();
    }

    static String requireValidTokenHash(String tokenHash) {
        if (!isValidTokenHash(tokenHash)) {
            throw new IllegalArgumentException(
                    "tokenHash must be exactly 64 lowercase hexadecimal characters");
        }
        return tokenHash;
    }

    private static boolean isValidTokenHash(String tokenHash) {
        return tokenHash != null && SHA_256_HEX.matcher(tokenHash).matches();
    }
}
