package com.amidog.app.admin;

import java.util.Objects;

public final class InvalidAdminRequestException extends RuntimeException {

    private final AdminBadRequestType type;

    public InvalidAdminRequestException(AdminBadRequestType type) {
        super(Objects.requireNonNull(type, "type").message());
        this.type = type;
    }

    public AdminBadRequestType getType() {
        return type;
    }
}
