package com.amidog.app.email;

import java.util.Objects;

public record EmailMessage(String to, String subject, String text, String replyTo) {

    public EmailMessage {
        Objects.requireNonNull(to);
        Objects.requireNonNull(subject);
        Objects.requireNonNull(text);
    }
}
