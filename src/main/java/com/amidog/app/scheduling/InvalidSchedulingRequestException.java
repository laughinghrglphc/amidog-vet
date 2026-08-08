package com.amidog.app.scheduling;

import java.util.Objects;

public class InvalidSchedulingRequestException extends RuntimeException {

    private final SchedulingBadRequestType type;

    public InvalidSchedulingRequestException(SchedulingBadRequestType type) {
        super(Objects.requireNonNull(type, "type").message());
        this.type = type;
    }

    public SchedulingBadRequestType getType() {
        return type;
    }
}
