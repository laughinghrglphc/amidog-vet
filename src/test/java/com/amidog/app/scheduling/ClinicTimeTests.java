package com.amidog.app.scheduling;

import com.amidog.app.common.api.ConflictException;
import com.amidog.app.common.api.ConflictType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.zone.ZoneOffsetTransition;
import java.time.zone.ZoneRules;
import java.util.List;
import java.util.TimeZone;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClinicTimeTests {

    private static final ZoneId SANTIAGO = ZoneId.of("America/Santiago");
    private final ClinicTime clinicTime = new ClinicTime(SANTIAGO);

    @Test
    void resolvesWinterAndSummerWithTheOffsetsDefinedBySantiagoRules() {
        LocalDateTime summer = LocalDateTime.parse("2026-01-12T09:00");
        LocalDateTime winter = LocalDateTime.parse("2026-07-13T09:00");

        assertThat(clinicTime.resolve(summer).getOffset())
                .isEqualTo(SANTIAGO.getRules().getValidOffsets(summer).getFirst());
        assertThat(clinicTime.resolve(winter).getOffset())
                .isEqualTo(SANTIAGO.getRules().getValidOffsets(winter).getFirst());
        assertThat(clinicTime.resolve(summer).getOffset())
                .isNotEqualTo(clinicTime.resolve(winter).getOffset());
    }

    @Test
    void rejectsALocalTimeInsideAnActualSantiagoGap() {
        ZoneOffsetTransition gap = transition(true);
        LocalDateTime nonexistent = gap.getDateTimeBefore().plusMinutes(1);

        assertThatThrownBy(() -> clinicTime.resolve(nonexistent))
                .isInstanceOfSatisfying(ConflictException.class, conflict -> {
                    assertThat(conflict.getType()).isEqualTo(ConflictType.NONEXISTENT_LOCAL_TIME);
                    assertThat(conflict.getCode()).isEqualTo("NONEXISTENT_LOCAL_TIME");
                });
        assertThat(clinicTime.resolveIfValid(nonexistent)).isEmpty();
    }

    @Test
    void deliberatelyChoosesTheEarlierOccurrenceInsideAnActualOverlap() {
        ZoneOffsetTransition overlap = transition(false);
        LocalDateTime ambiguous = overlap.getDateTimeAfter().plusMinutes(1);
        List<ZoneOffset> validOffsets = SANTIAGO.getRules().getValidOffsets(ambiguous);

        assertThat(validOffsets).hasSize(2);
        assertThat(clinicTime.resolve(ambiguous).getOffset()).isEqualTo(validOffsets.getFirst());
        assertThat(clinicTime.resolve(ambiguous).toInstant())
                .isBefore(ambiguous.atOffset(validOffsets.getLast()).toInstant());
    }

    @Test
    void conversionsIgnoreTheJvmDefaultTimezoneAndRetainTheActualClinicOffset() {
        TimeZone previous = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"));
            Instant instant = Instant.parse("2026-07-13T13:00:00Z");

            assertThat(clinicTime.localDate(instant).toString()).isEqualTo("2026-07-13");
            assertThat(clinicTime.toOffsetDateTime(instant).getOffset())
                    .isEqualTo(SANTIAGO.getRules().getOffset(instant));
            assertThat(clinicTime.toOffsetDateTime(instant).toLocalTime().toString())
                    .isEqualTo("09:00");
        } finally {
            TimeZone.setDefault(previous);
        }
    }

    private ZoneOffsetTransition transition(boolean gap) {
        ZoneRules rules = SANTIAGO.getRules();
        ZoneOffsetTransition cursor = rules.nextTransition(Instant.parse("2020-01-01T00:00:00Z"));
        while (cursor != null && cursor.getInstant().isBefore(Instant.parse("2040-01-01T00:00:00Z"))) {
            if (gap ? cursor.isGap() : cursor.isOverlap()) {
                return cursor;
            }
            cursor = rules.nextTransition(cursor.getInstant().plusSeconds(1));
        }
        throw new AssertionError("America/Santiago had no expected transition in the test window");
    }
}
