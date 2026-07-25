package com.amidog.app.scheduling;

import com.amidog.app.common.api.ConflictException;
import com.amidog.app.common.api.ConflictType;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.amidog.app.scheduling.AvailabilityDtos.AvailabilityBlockRequest;
import static com.amidog.app.scheduling.AvailabilityDtos.WeeklyIntervalRequest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AvailabilityAdministrationServiceTests {

    private static final Instant NOW = Instant.parse("2026-07-29T12:00:00Z");
    private static final ClinicTime CLINIC_TIME =
            new ClinicTime(ZoneId.of("America/Santiago"));

    @Test
    void blockCreationLocksThenChecksOccupiedReservationsThenSavesTrimmedData() {
        List<String> calls = new ArrayList<>();
        AvailabilityBlockRepository blocks = repository(
                AvailabilityBlockRepository.class,
                (name, args) -> {
                    if (name.equals("saveAndFlush")) {
                        calls.add("save");
                        AvailabilityBlock block = (AvailabilityBlock) args[0];
                        setId(block, 9L);
                        return block;
                    }
                    throw new UnsupportedOperationException(name);
                });
        OccupiedSlotReader occupied = new OccupiedSlotReader(null) {
            @Override
            public List<OccupiedSlot> findOverlapping(Instant start, Instant end) {
                calls.add("occupied");
                return List.of();
            }
        };
        SchedulingMutationLock lock = () -> calls.add("lock");
        var service = new AvailabilityAdministrationService(
                emptyWeekly(), blocks, occupied, lock, CLINIC_TIME,
                Clock.fixed(NOW, ZoneOffset.UTC));

        var response = service.createBlock(new AvailabilityBlockRequest(
                OffsetDateTime.parse("2026-08-10T09:00:00-04:00"),
                OffsetDateTime.parse("2026-08-10T10:00:00-04:00"),
                "  Cirug\u00eda  "));

        assertThat(calls).containsExactly("lock", "occupied", "save");
        assertThat(response.id()).isEqualTo(9L);
        assertThat(response.reason()).isEqualTo("Cirug\u00eda");
    }

    @Test
    void overlappingReservationsReturnSortedValidatedIdsAndPreventSave() {
        List<String> calls = new ArrayList<>();
        AvailabilityBlockRepository blocks = repository(
                AvailabilityBlockRepository.class,
                (name, args) -> {
                    calls.add("save");
                    throw new AssertionError("conflicting block must not be saved");
                });
        OccupiedSlotReader occupied = new OccupiedSlotReader(null) {
            @Override
            public List<OccupiedSlot> findOverlapping(Instant start, Instant end) {
                calls.add("occupied");
                return List.of(
                        new OccupiedSlot(12L, start, end),
                        new OccupiedSlot(4L, start, end),
                        new OccupiedSlot(12L, start, end));
            }
        };
        var service = new AvailabilityAdministrationService(
                emptyWeekly(), blocks, occupied, () -> calls.add("lock"), CLINIC_TIME,
                Clock.fixed(NOW, ZoneOffset.UTC));

        assertThatThrownBy(() -> service.createBlock(new AvailabilityBlockRequest(
                OffsetDateTime.parse("2026-08-10T09:00:00-04:00"),
                OffsetDateTime.parse("2026-08-10T10:00:00-04:00"),
                null)))
                .isInstanceOfSatisfying(ConflictException.class, conflict -> {
                    assertThat(conflict.getType()).isEqualTo(ConflictType.BLOCK_OVERLAPS_RESERVATIONS);
                    assertThat(conflict.getReservationIds()).containsExactly(4L, 12L);
                });
        assertThat(calls).containsExactly("lock", "occupied");
    }

    @Test
    void weeklyReplacementUsesTheSharedLockAndRejectsOnlyActiveOverlap() {
        List<String> calls = new ArrayList<>();
        WeeklyAvailabilityRepository weekly = repository(
                WeeklyAvailabilityRepository.class,
                (name, args) -> switch (name) {
                    case "deleteAllInBatch" -> {
                        calls.add("delete");
                        yield null;
                    }
                    case "saveAllAndFlush" -> {
                        calls.add("save");
                        @SuppressWarnings("unchecked")
                        List<WeeklyAvailability> saved = (List<WeeklyAvailability>) args[0];
                        yield saved;
                    }
                    default -> throw new UnsupportedOperationException(name);
                });
        var service = new AvailabilityAdministrationService(
                weekly, emptyBlocks(), noOccupied(), () -> calls.add("lock"), CLINIC_TIME,
                Clock.fixed(NOW, ZoneOffset.UTC));

        service.replaceWeekly(List.of(
                new WeeklyIntervalRequest(1, LocalTime.of(9, 0), LocalTime.of(11, 0), true),
                new WeeklyIntervalRequest(1, LocalTime.of(10, 0), LocalTime.of(12, 0), false),
                new WeeklyIntervalRequest(1, LocalTime.of(11, 0), LocalTime.of(12, 0), true)));

        assertThat(calls).containsExactly("lock", "delete", "save");

        assertThatThrownBy(() -> service.replaceWeekly(List.of(
                new WeeklyIntervalRequest(1, LocalTime.of(9, 0), LocalTime.of(11, 0), true),
                new WeeklyIntervalRequest(1, LocalTime.of(10, 30), LocalTime.of(12, 0), true))))
                .isInstanceOfSatisfying(ConflictException.class,
                        conflict -> assertThat(conflict.getType())
                                .isEqualTo(ConflictType.WEEKLY_INTERVAL_OVERLAP));
    }

    @Test
    void emptyWeeklyReplacementClosesAllAndMissingBlockDeleteIsNotFound() {
        List<String> calls = new ArrayList<>();
        WeeklyAvailabilityRepository weekly = repository(
                WeeklyAvailabilityRepository.class,
                (name, args) -> switch (name) {
                    case "deleteAllInBatch" -> {
                        calls.add("delete");
                        yield null;
                    }
                    case "saveAllAndFlush" -> {
                        calls.add("save");
                        yield List.of();
                    }
                    default -> throw new UnsupportedOperationException(name);
                });
        AvailabilityBlockRepository blocks = repository(
                AvailabilityBlockRepository.class,
                (name, args) -> {
                    if (name.equals("findById")) {
                        return Optional.empty();
                    }
                    throw new UnsupportedOperationException(name);
                });
        var service = new AvailabilityAdministrationService(
                weekly, blocks, noOccupied(), () -> calls.add("lock"), CLINIC_TIME,
                Clock.fixed(NOW, ZoneOffset.UTC));

        assertThat(service.replaceWeekly(List.of())).isEmpty();
        assertThat(calls).containsExactly("lock", "delete", "save");
        assertThatThrownBy(() -> service.deleteBlock(77L))
                .isInstanceOf(com.amidog.app.common.api.NotFoundException.class);
    }

    private static WeeklyAvailabilityRepository emptyWeekly() {
        return repository(WeeklyAvailabilityRepository.class, (name, args) -> {
            throw new UnsupportedOperationException(name);
        });
    }

    private static AvailabilityBlockRepository emptyBlocks() {
        return repository(AvailabilityBlockRepository.class, (name, args) -> {
            throw new UnsupportedOperationException(name);
        });
    }

    private static OccupiedSlotReader noOccupied() {
        return new OccupiedSlotReader(null) {
            @Override
            public List<OccupiedSlot> findOverlapping(Instant start, Instant end) {
                return List.of();
            }
        };
    }

    @SuppressWarnings("unchecked")
    private static <T> T repository(Class<T> type, Invocation invocation) {
        return (T) Proxy.newProxyInstance(
                type.getClassLoader(), new Class<?>[]{type},
                (proxy, method, args) -> {
                    if (method.getDeclaringClass() == Object.class) {
                        return switch (method.getName()) {
                            case "toString" -> type.getSimpleName() + "Fake";
                            case "hashCode" -> System.identityHashCode(proxy);
                            case "equals" -> proxy == args[0];
                            default -> null;
                        };
                    }
                    return invocation.invoke(method.getName(), args);
                });
    }

    private static void setId(AvailabilityBlock block, long id) {
        try {
            Field field = AvailabilityBlock.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(block, id);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }

    @FunctionalInterface
    private interface Invocation {
        Object invoke(String name, Object[] args);
    }
}
