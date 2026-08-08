package com.amidog.app.common.api;

public class NotFoundException extends RuntimeException {

    public NotFoundException(String clientSafeMessage) {
        super(clientSafeMessage);
    }
}
