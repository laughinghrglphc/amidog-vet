package com.amidog.app.scheduling;

/**
 * Closed set of client-safe scheduling request failures.
 */
public enum SchedulingBadRequestType {
    INVALID_DATE_RANGE(
            "INVALID_DATE_RANGE",
            "La fecha inicial no puede ser posterior a la fecha final."),
    INVALID_WEEKLY_INTERVAL(
            "INVALID_WEEKLY_INTERVAL",
            "Cada intervalo debe tener un inicio anterior a su fin."),
    DUPLICATE_WEEKLY_INTERVAL(
            "DUPLICATE_WEEKLY_INTERVAL",
            "No se puede repetir el mismo intervalo semanal."),
    INVALID_BLOCK_RANGE(
            "INVALID_BLOCK_RANGE",
            "El bloqueo debe tener un inicio anterior a su fin."),
    INVALID_BLOCK_REASON(
            "INVALID_BLOCK_REASON",
            "El motivo del bloqueo no puede superar 200 caracteres."),
    INVALID_RESERVATION_ITEMS(
            "INVALID_RESERVATION_ITEMS",
            "La reserva debe incluir entre una y diez mascotas con un servicio cada una."),
    DUPLICATE_RESERVATION_PET(
            "DUPLICATE_RESERVATION_PET",
            "Cada mascota puede aparecer una sola vez en la reserva."),
    INVALID_RESERVATION_START(
            "INVALID_RESERVATION_START",
            "La fecha y hora no corresponden al horario oficial de Chile."),
    INVALID_RESERVATION_STATUS_CHANGE(
            "INVALID_RESERVATION_STATUS_CHANGE",
            "El nuevo estado de la reserva es obligatorio.");

    private final String code;
    private final String message;

    SchedulingBadRequestType(String code, String message) {
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
