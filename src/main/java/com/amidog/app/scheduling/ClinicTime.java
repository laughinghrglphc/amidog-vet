package com.amidog.app.scheduling;

import com.amidog.app.common.api.ConflictException;
import com.amidog.app.common.api.ConflictType;
import com.amidog.app.config.AmidogProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

@Component
public class ClinicTime {

    private final ZoneId zone;

    @Autowired
    public ClinicTime(AmidogProperties properties) {
        this(properties.clinicZone());
    }

    ClinicTime(ZoneId zone) {
        this.zone = zone;
    }

    public ZonedDateTime resolve(LocalDateTime local) {
        List<ZoneOffset> offsets = zone.getRules().getValidOffsets(local);
        if (offsets.isEmpty()) {
            throw new ConflictException(ConflictType.NONEXISTENT_LOCAL_TIME);
        }
        return ZonedDateTime.ofLocal(local, zone, offsets.getFirst());
    }

    Optional<ZonedDateTime> resolveIfValid(LocalDateTime local) {
        List<ZoneOffset> offsets = zone.getRules().getValidOffsets(local);
        if (offsets.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(ZonedDateTime.ofLocal(local, zone, offsets.getFirst()));
    }

    public LocalDate localDate(Instant instant) {
        return instant.atZone(zone).toLocalDate();
    }

    public OffsetDateTime toOffsetDateTime(Instant instant) {
        return instant.atZone(zone).toOffsetDateTime();
    }

    public ZonedDateTime startOfDay(LocalDate date) {
        return date.atStartOfDay(zone);
    }

    public ZoneId zone() {
        return zone;
    }
}
