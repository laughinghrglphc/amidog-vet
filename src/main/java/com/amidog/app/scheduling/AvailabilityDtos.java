package com.amidog.app.scheduling;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalTime;
import java.time.OffsetDateTime;

public final class AvailabilityDtos {

    private AvailabilityDtos() {
    }

    public record AvailableSlotResponse(
            OffsetDateTime startsAt,
            OffsetDateTime endsAt) {
    }

    public record WeeklyIntervalRequest(
            @Min(1) @Max(7) int dayOfWeek,
            @NotNull LocalTime start,
            @NotNull LocalTime end,
            boolean active) {
    }

    public record WeeklyIntervalResponse(
            Long id,
            int dayOfWeek,
            LocalTime start,
            LocalTime end,
            boolean active) {
        static WeeklyIntervalResponse from(WeeklyAvailability interval) {
            return new WeeklyIntervalResponse(
                    interval.getId(),
                    interval.getDayOfWeek(),
                    interval.getLocalStartTime(),
                    interval.getLocalEndTime(),
                    interval.isActive());
        }
    }

    public record AvailabilityBlockRequest(
            @NotNull OffsetDateTime startsAt,
            @NotNull OffsetDateTime endsAt,
            @Size(max = 200) String reason) {
    }

    public record AvailabilityBlockResponse(
            Long id,
            OffsetDateTime startsAt,
            OffsetDateTime endsAt,
            String reason,
            OffsetDateTime createdAt) {
        static AvailabilityBlockResponse from(
                AvailabilityBlock block, ClinicTime clinicTime) {
            return new AvailabilityBlockResponse(
                    block.getId(),
                    clinicTime.toOffsetDateTime(block.getStartAt()),
                    clinicTime.toOffsetDateTime(block.getEndAt()),
                    block.getReason(),
                    clinicTime.toOffsetDateTime(block.getCreatedAt()));
        }
    }
}
