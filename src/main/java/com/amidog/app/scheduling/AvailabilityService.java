package com.amidog.app.scheduling;

import com.amidog.app.config.AmidogProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.amidog.app.scheduling.AvailabilityDtos.AvailableSlotResponse;

@Service
public class AvailabilityService {

    private final WeeklyAvailabilityRepository weekly;
    private final AvailabilityBlockRepository blocks;
    private final OccupiedSlotReader occupiedSlots;
    private final ClinicTime clinicTime;
    private final AmidogProperties properties;
    private final Clock clock;

    public AvailabilityService(
            WeeklyAvailabilityRepository weekly,
            AvailabilityBlockRepository blocks,
            OccupiedSlotReader occupiedSlots,
            ClinicTime clinicTime,
            AmidogProperties properties,
            Clock clock) {
        this.weekly = weekly;
        this.blocks = blocks;
        this.occupiedSlots = occupiedSlots;
        this.clinicTime = clinicTime;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<AvailableSlotResponse> availableSlots(
            LocalDate requestedFrom, LocalDate requestedTo) {
        return availableSlots(requestedFrom, requestedTo, null);
    }

    @Transactional(readOnly = true)
    public List<AvailableSlotResponse> availableSlots(
            LocalDate requestedFrom,
            LocalDate requestedTo,
            Long excludedReservationId) {
        if (requestedFrom == null || requestedTo == null
                || requestedFrom.isAfter(requestedTo)) {
            throw new InvalidSchedulingRequestException(
                    SchedulingBadRequestType.INVALID_DATE_RANGE);
        }

        Instant now = clock.instant();
        LocalDate today = clinicTime.localDate(now);
        LocalDate horizon = today.plusDays(properties.booking().horizonDays());
        LocalDate from = requestedFrom.isBefore(today) ? today : requestedFrom;
        LocalDate to = requestedTo.isAfter(horizon) ? horizon : requestedTo;
        if (from.isAfter(to)) {
            return List.of();
        }

        Instant rangeStart = clinicTime.startOfDay(from).toInstant();
        Instant rangeEnd = clinicTime.startOfDay(to.plusDays(1)).toInstant();
        List<TimeRange> blocked = blocks.findOverlapping(rangeStart, rangeEnd)
                .stream()
                .map(block -> new TimeRange(block.getStartAt(), block.getEndAt()))
                .toList();
        List<TimeRange> occupied = (excludedReservationId == null
                ? occupiedSlots.findOverlapping(rangeStart, rangeEnd)
                : occupiedSlots.findOverlappingExcluding(
                        rangeStart, rangeEnd, excludedReservationId))
                .stream()
                .map(slot -> new TimeRange(slot.start(), slot.end()))
                .toList();
        Map<Integer, List<WeeklyAvailability>> byDay = new LinkedHashMap<>();
        weekly.findAllByActiveTrueOrderByDayOfWeekAscLocalStartTimeAscLocalEndTimeAsc()
                .forEach(interval ->
                        byDay.computeIfAbsent(
                                        (int) interval.getDayOfWeek(),
                                        ignored -> new ArrayList<>())
                                .add(interval));

        Instant noticeBoundary = now.plus(
                Duration.ofHours(properties.booking().minimumNoticeHours()));
        int durationMinutes = properties.booking().durationMinutes();
        Map<Instant, AvailableSlotResponse> unique = new LinkedHashMap<>();
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            for (WeeklyAvailability interval :
                    byDay.getOrDefault(date.getDayOfWeek().getValue(), List.of())) {
                generateInterval(
                        date,
                        interval.getLocalStartTime(),
                        interval.getLocalEndTime(),
                        durationMinutes,
                        noticeBoundary,
                        blocked,
                        occupied,
                        unique);
            }
        }
        return unique.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(Map.Entry::getValue)
                .toList();
    }

    private void generateInterval(
            LocalDate date,
            LocalTime intervalStart,
            LocalTime intervalEnd,
            int durationMinutes,
            Instant noticeBoundary,
            List<TimeRange> blocked,
            List<TimeRange> occupied,
            Map<Instant, AvailableSlotResponse> unique) {
        for (LocalDateTime localStart = date.atTime(intervalStart);
             !localStart.plusMinutes(durationMinutes).toLocalTime().isAfter(intervalEnd)
                     && localStart.plusMinutes(durationMinutes).toLocalDate().equals(date);
             localStart = localStart.plusMinutes(durationMinutes)) {
            var resolved = clinicTime.resolveIfValid(localStart);
            if (resolved.isEmpty()) {
                continue;
            }
            Instant start = resolved.orElseThrow().toInstant();
            Instant end = start.plus(Duration.ofMinutes(durationMinutes));
            TimeRange candidate = new TimeRange(start, end);
            if (start.isBefore(noticeBoundary)
                    || overlapsAny(candidate, blocked)
                    || overlapsAny(candidate, occupied)) {
                continue;
            }
            unique.putIfAbsent(
                    start,
                    new AvailableSlotResponse(
                            clinicTime.toOffsetDateTime(start),
                            clinicTime.toOffsetDateTime(end)));
        }
    }

    private static boolean overlapsAny(TimeRange candidate, List<TimeRange> ranges) {
        return ranges.stream().anyMatch(candidate::overlaps);
    }

    public record TimeRange(Instant start, Instant end) {
        public TimeRange {
            if (start == null || end == null || !end.isAfter(start)) {
                throw new IllegalArgumentException("time range end must be after start");
            }
        }

        boolean overlaps(TimeRange other) {
            return start.isBefore(other.end) && end.isAfter(other.start);
        }
    }
}
