package com.amidog.app.common.api;

/**
 * Closed set of reviewed, client-safe conflicts. New public conflict responses
 * must add a reviewed descriptor here rather than accepting runtime text.
 */
public enum ConflictType {
    SERVICE_CODE_EXISTS(
            "SERVICE_CODE_EXISTS",
            "Ya existe un servicio con ese código."),
    SERVICE_CONCURRENT_UPDATE(
            "SERVICE_CONCURRENT_UPDATE",
            "El servicio fue modificado por otra operación."),
    CLIENT_CONCURRENT_UPDATE(
            "CLIENT_CONCURRENT_UPDATE",
            "El perfil fue modificado por otra operación."),
    PET_CONCURRENT_UPDATE(
            "PET_CONCURRENT_UPDATE",
            "La mascota fue modificada por otra operación."),
    PET_HAS_FUTURE_RESERVATION(
            "PET_HAS_FUTURE_RESERVATION",
            "Cancela o resuelve primero las reservas futuras de esta mascota."),
    NONEXISTENT_LOCAL_TIME(
            "NONEXISTENT_LOCAL_TIME",
            "La hora no existe por el cambio de horario en Chile."),
    WEEKLY_INTERVAL_OVERLAP(
            "WEEKLY_INTERVAL_OVERLAP",
            "Los horarios activos del mismo día no pueden superponerse."),
    BLOCK_OVERLAPS_RESERVATIONS(
            "BLOCK_OVERLAPS_RESERVATIONS",
            "El bloqueo se superpone con reservas activas."),
    SLOT_UNAVAILABLE(
            "SLOT_UNAVAILABLE",
            "El horario seleccionado ya no est\u00e1 disponible."),
    SLOT_ALREADY_BOOKED(
            "SLOT_ALREADY_BOOKED",
            "Ese horario acaba de ser reservado. Elige otro bloque disponible."),
    INVALID_RESERVATION_STATUS_TRANSITION(
            "INVALID_RESERVATION_STATUS_TRANSITION",
            "El cambio de estado solicitado no est\u00e1 permitido."),
    RESERVATION_NOT_IN_FUTURE(
            "RESERVATION_NOT_IN_FUTURE",
            "Solo se pueden modificar reservas futuras."),
    RESERVATION_START_UNCHANGED(
            "RESERVATION_START_UNCHANGED",
            "La nueva hora debe ser distinta de la hora actual.");

    private final String code;
    private final String message;

    ConflictType(String code, String message) {
        this.code = code;
        this.message = message;
    }

    public String code() {
        return code;
    }

    public String message() {
        return message;
    }
}
