package com.amidog.app.notification;

import com.amidog.app.auth.UserAccount;
import com.amidog.app.reservation.Reservation;
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

import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "notifications")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipient_user_id", nullable = false)
    private UserAccount recipient;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private NotificationType type;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(nullable = false, length = 500)
    private String body;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reservation_id")
    private Reservation reservation;

    @Column(name = "deduplication_key", nullable = false, unique = true,
            length = 160)
    private String deduplicationKey;

    @Column(name = "read_at")
    private Instant readAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public static Notification create(
            UserAccount recipient,
            NotificationType type,
            String title,
            String body,
            Reservation reservation,
            String deduplicationKey,
            Instant createdAt) {
        return new Notification(
                recipient, type, title, body, reservation,
                deduplicationKey, createdAt);
    }

    private Notification(
            UserAccount recipient,
            NotificationType type,
            String title,
            String body,
            Reservation reservation,
            String deduplicationKey,
            Instant createdAt) {
        this.recipient = Objects.requireNonNull(recipient, "recipient");
        this.type = Objects.requireNonNull(type, "type");
        this.title = requireText(title, 120, "title");
        this.body = requireText(body, 500, "body");
        this.reservation = reservation;
        this.deduplicationKey =
                requireText(deduplicationKey, 160, "deduplicationKey");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }

    public void markRead(Instant now) {
        if (readAt == null) {
            readAt = Objects.requireNonNull(now, "now");
        }
    }

    private static String requireText(
            String value, int maximum, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    field + " must not be blank");
        }
        String normalized = value.trim();
        if (normalized.length() > maximum) {
            throw new IllegalArgumentException(
                    field + " must not exceed " + maximum + " characters");
        }
        return normalized;
    }
}
