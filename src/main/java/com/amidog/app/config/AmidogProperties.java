package com.amidog.app.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.validation.annotation.Validated;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.ZoneId;

@ConfigurationProperties(prefix = "amidog")
@Validated
public record AmidogProperties(
        ZoneId clinicZone,
        URI frontendBaseUrl,
        URI frontendOrigin,
        @Valid Booking booking,
        @Valid Admin admin,
        @Valid Contact contact,
        @Valid Email email
) {

    public AmidogProperties {
        validateFrontendOrigin(frontendOrigin);
    }

    private static void validateFrontendOrigin(URI origin) {
        if (origin == null
                || origin.isOpaque()
                || (!"http".equalsIgnoreCase(origin.getScheme())
                && !"https".equalsIgnoreCase(origin.getScheme()))
                || origin.getHost() == null
                || origin.getHost().isBlank()
                || origin.getRawUserInfo() != null
                || (origin.getRawPath() != null && !origin.getRawPath().isEmpty())
                || origin.getRawQuery() != null
                || origin.getRawFragment() != null
                || origin.toASCIIString().contains("*")
                || origin.getPort() == 0
                || origin.getPort() < -1
                || origin.getPort() > 65_535
                || !hasExactOriginSerialization(origin)) {
            throw new IllegalArgumentException(
                    "amidog.frontend-origin must be one exact HTTP(S) origin without credentials, wildcards, path, query, or fragment");
        }
    }

    private static boolean hasExactOriginSerialization(URI origin) {
        try {
            URI parsedComponentsOnly = new URI(
                    origin.getScheme(),
                    null,
                    origin.getHost(),
                    origin.getPort(),
                    null,
                    null,
                    null);
            return origin.toASCIIString()
                    .equals(parsedComponentsOnly.toASCIIString());
        } catch (URISyntaxException invalidParsedComponents) {
            return false;
        }
    }

    public record Booking(
            boolean autoConfirm,
            @Min(30) @Max(30) int durationMinutes,
            @PositiveOrZero int minimumNoticeHours,
            @Positive int horizonDays
    ) {
    }

    public record Admin(String email, String password, String name, String phone) {
    }

    public record Contact(String clinicEmail, String whatsappNumber) {
    }

    public record Email(String delivery, @Valid Smtp smtp) {
        @ConstructorBinding
        public Email {
        }

        public Email(String delivery) {
            this(delivery, null);
        }
    }

    public record Smtp(
            String host,
            int port,
            String username,
            String password,
            boolean auth,
            boolean starttls,
            boolean starttlsRequired,
            boolean ssl,
            boolean allowPlaintext
    ) {
        public void validateForDelivery() {
            if (host == null || host.isBlank()) {
                throw new IllegalStateException("SMTP_HOST is required when EMAIL_DELIVERY=smtp");
            }
            if (port < 1 || port > 65_535) {
                throw new IllegalStateException("SMTP_PORT must be between 1 and 65535");
            }
            if (starttls && ssl) {
                throw new IllegalStateException("SMTP_STARTTLS and SMTP_SSL are mutually exclusive");
            }
            if (starttlsRequired && !starttls) {
                throw new IllegalStateException("SMTP_STARTTLS_REQUIRED requires SMTP_STARTTLS");
            }
            boolean hasUsername = username != null && !username.isBlank();
            boolean hasPassword = password != null && !password.isBlank();
            if (auth && (username == null || username.isBlank() || password == null || password.isBlank())) {
                throw new IllegalStateException("SMTP authentication requires SMTP_USERNAME and SMTP_PASSWORD");
            }
            if (!auth && (hasUsername || hasPassword)) {
                throw new IllegalStateException(
                        "SMTP_USERNAME and SMTP_PASSWORD must be blank when SMTP_AUTH=false");
            }
            boolean local = isLocalHost(host);
            if ((!local || auth) && !ssl && !(starttls && starttlsRequired)) {
                throw new IllegalStateException(
                        "Remote or authenticated SMTP requires implicit SSL or required STARTTLS");
            }
            if (!starttls && !ssl) {
                if (!allowPlaintext || !local || auth) {
                    throw new IllegalStateException(
                            "Plaintext SMTP requires SMTP_ALLOW_PLAINTEXT=true, a localhost host, and SMTP_AUTH=false");
                }
            }
            if (local && starttls && !starttlsRequired && !allowPlaintext) {
                throw new IllegalStateException(
                        "Optional local STARTTLS requires SMTP_ALLOW_PLAINTEXT=true");
            }
        }

        private static boolean isLocalHost(String host) {
            return "localhost".equalsIgnoreCase(host)
                    || "127.0.0.1".equals(host)
                    || "::1".equals(host);
        }

        @Override
        public String toString() {
            return "Smtp[host=" + host + ", port=" + port + ", username=redacted, password=redacted"
                    + ", auth=" + auth + ", starttls=" + starttls
                    + ", starttlsRequired=" + starttlsRequired + ", ssl=" + ssl
                    + ", allowPlaintext=" + allowPlaintext + "]";
        }
    }
}
