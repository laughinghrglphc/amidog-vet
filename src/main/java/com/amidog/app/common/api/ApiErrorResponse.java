package com.amidog.app.common.api;

import java.util.Map;
import java.util.Objects;

public record ApiErrorResponse(
        String code,
        String message,
        Map<String, String> errors
) {
    public ApiErrorResponse {
        code = Objects.requireNonNull(code, "code");
        message = Objects.requireNonNull(message, "message");
        errors = errors == null ? Map.of() : Map.copyOf(errors);
    }

    public static ApiErrorResponse of(String code, String message) {
        return new ApiErrorResponse(code, message, Map.of());
    }
}
