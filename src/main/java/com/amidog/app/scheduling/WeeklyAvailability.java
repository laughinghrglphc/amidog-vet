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

import java.time.LocalTime;

@Entity
@Table(name = "weekly_availability")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WeeklyAvailability {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "day_of_week", nullable = false)
    private short dayOfWeek;

    @Column(name = "local_start_time", nullable = false)
    private LocalTime localStartTime;

    @Column(name = "local_end_time", nullable = false)
    private LocalTime localEndTime;

    @Column(nullable = false)
    private boolean active;

    public static WeeklyAvailability create(
            int dayOfWeek, LocalTime start, LocalTime end, boolean active) {
        return new WeeklyAvailability(dayOfWeek, start, end, active);
    }

    private WeeklyAvailability(
            int dayOfWeek, LocalTime start, LocalTime end, boolean active) {
        if (dayOfWeek < 1 || dayOfWeek > 7) {
            throw new IllegalArgumentException("dayOfWeek must be between 1 and 7");
        }
        if (start == null || end == null || !end.isAfter(start)) {
            throw new InvalidSchedulingRequestException(
                    SchedulingBadRequestType.INVALID_WEEKLY_INTERVAL);
        }
        this.dayOfWeek = (short) dayOfWeek;
        this.localStartTime = start;
        this.localEndTime = end;
        this.active = active;
    }
}
