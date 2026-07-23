package com.amidog.app.admin;

public enum AdminBadRequestType {
    INVALID_ADMIN_PAGINATION(
            "INVALID_ADMIN_PAGINATION",
            "La paginaci\u00f3n solicitada no es v\u00e1lida."),
    INVALID_ADMIN_SEARCH(
            "INVALID_ADMIN_SEARCH",
            "La b\u00fasqueda debe contener como m\u00e1ximo 120 caracteres."),
    INVALID_ADMIN_DATE_RANGE(
            "INVALID_ADMIN_DATE_RANGE",
            "El rango de fechas solicitado no es v\u00e1lido."),
    INVALID_ADMIN_FILTER(
            "INVALID_ADMIN_FILTER",
            "El filtro solicitado no es v\u00e1lido.");

    private final String code;
    private final String message;

    AdminBadRequestType(String code, String message) {
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
