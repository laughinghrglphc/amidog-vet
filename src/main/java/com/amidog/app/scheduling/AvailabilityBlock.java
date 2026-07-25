package com.amidog.app.scheduling;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "availability_blocks")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AvailabilityBlock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "start_at", nullable = false)
    private Instant startAt;

    @Column(name = "end_at", nullable = false)
    private Instant endAt;

    @Column(length = 200)
    private String reason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public static AvailabilityBlock create(
            Instant start, Instant end, String reason, Instant createdAt) {
        return new AvailabilityBlock(start, end, reason, createdAt);
    }

    private AvailabilityBlock(
            Instant start, Instant end, String reason, Instant createdAt) {
        if (start == null || end == null || !end.isAfter(start)) {
            throw new InvalidSchedulingRequestException(
                    SchedulingBadRequestType.INVALID_BLOCK_RANGE);
        }
        String normalizedReason =
                reason == null || reason.trim().isEmpty() ? null : reason.trim();
        if (normalizedReason != null && normalizedReason.length() > 200) {
            throw new InvalidSchedulingRequestException(
                    SchedulingBadRequestType.INVALID_BLOCK_REASON);
        }
        if (createdAt == null) {
            throw new IllegalArgumentException("createdAt must not be null");
        }
        this.startAt = start;
        this.endAt = end;
        this.reason = normalizedReason;
        this.createdAt = createdAt;
    }
}
