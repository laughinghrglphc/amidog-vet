package com.amidog.app.common.api;

public class TooManyRequestsException extends RuntimeException {

    public static final String MESSAGE =
            "Demasiadas solicitudes. Intenta nuevamente m\u00e1s tarde.";

    public TooManyRequestsException() {
        super(MESSAGE);
    }
}
