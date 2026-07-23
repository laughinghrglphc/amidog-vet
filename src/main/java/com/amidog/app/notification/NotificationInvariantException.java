package com.amidog.app.notification;

public final class NotificationInvariantException
        extends IllegalStateException {

    public NotificationInvariantException() {
        super("The notification recipient invariant is not satisfied");
    }
}
