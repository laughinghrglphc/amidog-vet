package com.amidog.app.scheduling;

import com.amidog.app.common.api.ConflictException;
import com.amidog.app.common.api.ConflictType;
import com.amidog.app.common.api.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.amidog.app.scheduling.AvailabilityDtos.AvailabilityBlockRequest;
import static com.amidog.app.scheduling.AvailabilityDtos.AvailabilityBlockResponse;
import static com.amidog.app.scheduling.AvailabilityDtos.WeeklyIntervalRequest;
import static com.amidog.app.scheduling.AvailabilityDtos.WeeklyIntervalResponse;

@Service
public class AvailabilityAdministrationService {

    static final String BLOCK_NOT_FOUND = "No se encontr\u00f3 el bloqueo de agenda.";

    private final WeeklyAvailabilityRepository weekly;
    private final AvailabilityBlockRepository blocks;
    private final OccupiedSlotReader occupiedSlots;
    private final SchedulingMutationLock mutationLock;
    private final ClinicTime clinicTime;
    private final Clock clock;

    public AvailabilityAdministrationService(
            WeeklyAvailabilityRepository weekly,
            AvailabilityBlockRepository blocks,
            OccupiedSlotReader occupiedSlots,
            SchedulingMutationLock mutationLock,
            ClinicTime clinicTime,
            Clock clock) {
        this.weekly = weekly;
        this.blocks = blocks;
        this.occupiedSlots = occupiedSlots;
        this.mutationLock = mutationLock;
        this.clinicTime = clinicTime;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<WeeklyIntervalResponse> weeklySchedule() {
        return weekly.findAllByOrderByDayOfWeekAscLocalStartTimeAscLocalEndTimeAsc()
                .stream()
                .map(WeeklyIntervalResponse::from)
                .toList();
    }

    /**
     * Full schedule replacement. Inactive entries remain stored and visible to
     * administrators but never generate slots. Active entries for one day may
     * be adjacent, but may not overlap. The scheduling mutation lock makes the
     * replacement a coherent point relative to reservation and block writers;
     * existing reservations are deliberately left unchanged.
     */
    @Transactional
    public List<WeeklyIntervalResponse> replaceWeekly(
            List<WeeklyIntervalRequest> requests) {
        List<WeeklyIntervalRequest> safeRequests =
                requests == null ? List.of() : List.copyOf(requests);
        validateWeekly(safeRequests);
        List<WeeklyAvailability> replacements = safeRequests.stream()
                .map(request -> WeeklyAvailability.create(
                        request.dayOfWeek(),
                        request.start(),
                        request.end(),
                        request.active()))
                .toList();

        mutationLock.acquire();
        weekly.deleteAllInBatch();
        List<WeeklyAvailability> saved = weekly.saveAllAndFlush(replacements);
        return saved.stream()
                .sorted(Comparator
                        .comparingInt(WeeklyAvailability::getDayOfWeek)
                        .thenComparing(WeeklyAvailability::getLocalStartTime)
                        .thenComparing(WeeklyAvailability::getLocalEndTime))
                .map(WeeklyIntervalResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AvailabilityBlockResponse> listBlocks() {
        return blocks.findAllByOrderByStartAtAscEndAtAscIdAsc()
                .stream()
                .map(block -> AvailabilityBlockResponse.from(block, clinicTime))
                .toList();
    }

    @Transactional
    public AvailabilityBlockResponse createBlock(AvailabilityBlockRequest request) {
        if (request == null || request.startsAt() == null || request.endsAt() == null) {
            throw new InvalidSchedulingRequestException(
                    SchedulingBadRequestType.INVALID_BLOCK_RANGE);
        }
        Instant start = request.startsAt().toInstant();
        Instant end = request.endsAt().toInstant();
        AvailabilityBlock block =
                AvailabilityBlock.create(start, end, request.reason(), clock.instant());

        mutationLock.acquire();
        List<Long> conflicts = occupiedSlots.findOverlapping(start, end)
                .stream()
                .map(OccupiedSlotReader.OccupiedSlot::reservationId)
                .distinct()
                .sorted()
                .toList();
        if (!conflicts.isEmpty()) {
            throw ConflictException.blockOverlapsReservations(conflicts);
        }
        return AvailabilityBlockResponse.from(blocks.saveAndFlush(block), clinicTime);
    }

    @Transactional
    public void deleteBlock(long id) {
        mutationLock.acquire();
        AvailabilityBlock block = blocks.findById(id)
                .orElseThrow(() -> new NotFoundException(BLOCK_NOT_FOUND));
        blocks.delete(block);
        blocks.flush();
    }

    private static void validateWeekly(List<WeeklyIntervalRequest> requests) {
        Set<IntervalKey> unique = new HashSet<>();
        List<WeeklyIntervalRequest> active = new ArrayList<>();
        for (WeeklyIntervalRequest request : requests) {
            if (request == null
                    || request.dayOfWeek() < 1
                    || request.dayOfWeek() > 7
                    || request.start() == null
                    || request.end() == null
                    || !request.end().isAfter(request.start())) {
                throw new InvalidSchedulingRequestException(
                        SchedulingBadRequestType.INVALID_WEEKLY_INTERVAL);
            }
            if (!unique.add(new IntervalKey(
                    request.dayOfWeek(), request.start(), request.end()))) {
                throw new InvalidSchedulingRequestException(
                        SchedulingBadRequestType.DUPLICATE_WEEKLY_INTERVAL);
            }
            if (request.active()) {
                active.add(request);
            }
        }
        active.sort(Comparator
                .comparingInt(WeeklyIntervalRequest::dayOfWeek)
                .thenComparing(WeeklyIntervalRequest::start)
                .thenComparing(WeeklyIntervalRequest::end));
        WeeklyIntervalRequest previous = null;
        for (WeeklyIntervalRequest current : active) {
            if (previous != null
                    && previous.dayOfWeek() == current.dayOfWeek()
                    && current.start().isBefore(previous.end())) {
                throw new ConflictException(ConflictType.WEEKLY_INTERVAL_OVERLAP);
            }
            previous = current;
        }
    }

    private record IntervalKey(int dayOfWeek, LocalTime start, LocalTime end) {
    }
}
