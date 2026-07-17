package com.amidog.app.common.api;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

public class ConflictException extends RuntimeException {

    private final ConflictType type;
    private final ReservationConflictDetails details;

    public ConflictException(String clientSafeMessage) {
        super(requireLegacyMessage(clientSafeMessage));
        this.type = null;
        this.details = null;
    }

    public ConflictException(ConflictType type) {
        super(Objects.requireNonNull(type, "type").message());
        this.type = type;
        this.details = null;
    }

    private ConflictException(ConflictType type, ReservationConflictDetails details) {
        super(Objects.requireNonNull(type, "type").message());
        if (type != ConflictType.BLOCK_OVERLAPS_RESERVATIONS) {
            throw new IllegalArgumentException("reservation IDs are only valid for block conflicts");
        }
        this.type = type;
        this.details = Objects.requireNonNull(details, "details");
    }

    public static ConflictException blockOverlapsReservations(Collection<Long> reservationIds) {
        return new ConflictException(
                ConflictType.BLOCK_OVERLAPS_RESERVATIONS,
                ReservationConflictDetails.from(reservationIds));
    }

    public String getCode() {
        return type == null ? "CONFLICT" : type.code();
    }

    public String getClientSafeMessage() {
        return type == null ? getMessage() : type.message();
    }

    public ConflictType getType() {
        return type;
    }

    public List<Long> getReservationIds() {
        return details == null ? List.of() : details.reservationIds();
    }

    public ReservationConflictDetails getDetails() {
        return details;
    }

    private static String requireLegacyMessage(String message) {
        Objects.requireNonNull(message, "clientSafeMessage");
        if (message.isBlank()) {
            throw new IllegalArgumentException("clientSafeMessage must not be blank");
        }
        return message;
    }

    public record ReservationConflictDetails(List<Long> reservationIds) {
        public ReservationConflictDetails {
            reservationIds = List.copyOf(reservationIds);
            if (reservationIds.isEmpty()
                    || reservationIds.stream().anyMatch(id -> id == null || id <= 0)
                    || reservationIds.stream().distinct().count() != reservationIds.size()) {
                throw new IllegalArgumentException(
                        "reservationIds must contain distinct positive identifiers");
            }
        }

        private static ReservationConflictDetails from(Collection<Long> reservationIds) {
            Objects.requireNonNull(reservationIds, "reservationIds");
            if (reservationIds.stream().anyMatch(Objects::isNull)) {
                throw new IllegalArgumentException("reservationIds must not contain null");
            }
            return new ReservationConflictDetails(
                    reservationIds.stream()
                            .distinct()
                            .sorted()
                            .toList());
        }
    }
}
