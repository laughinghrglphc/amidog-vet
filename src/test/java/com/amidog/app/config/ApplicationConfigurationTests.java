package com.amidog.app.config;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ApplicationConfigurationTests {

    @Test
    void normalConfigurationKeepsFlywayAndPostgresValidationAuthoritative() throws Exception {
        String yaml = Files.readString(Path.of("src/main/resources/application.yaml"));

        assertThat(yaml)
                .contains("flyway:", "enabled: true", "ddl-auto: validate",
                        "initialize-schema: never", "url: ${DB_URL}")
                .doesNotContain("jdbc:h2", "ddl-auto: create", "ddl-auto: update");
    }

    @Test
    void smtpDefaultsToLogDeliveryAndSecureStartTlsWhenEnabled() throws Exception {
        String yaml = Files.readString(Path.of("src/main/resources/application.yaml"));

        assertThat(yaml)
                .contains("delivery: ${EMAIL_DELIVERY:log}",
                        "port: ${SMTP_PORT:587}",
                        "auth: ${SMTP_AUTH:true}",
                        "starttls: ${SMTP_STARTTLS:true}",
                        "starttls-required: ${SMTP_STARTTLS_REQUIRED:true}",
                        "ssl: ${SMTP_SSL:false}",
                        "allow-plaintext: ${SMTP_ALLOW_PLAINTEXT:false}");
    }

    @Test
    void maintenanceDefaultsUseTheExactReminderWindowAndDailyCleanup()
            throws Exception {
        String yaml = Files.readString(
                Path.of("src/main/resources/application.yaml"));

        assertThat(yaml)
                .contains(
                        "clinic-zone: ${CLINIC_TIMEZONE:America/Santiago}",
                        "window-start-hours: ${NOTIFICATION_REMINDER_WINDOW_START_HOURS:23}",
                        "window-end-hours: ${NOTIFICATION_REMINDER_WINDOW_END_HOURS:25}",
                        "cron: ${NOTIFICATION_REMINDER_CRON:0 5 * * * *}",
                        "cleanup-cron: ${TOKEN_CLEANUP_CRON:0 30 3 * * *}")
                .doesNotContain(
                        "NOTIFICATION_REMINDER_LEAD_HOURS",
                        "NOTIFICATION_REMINDER_WINDOW_MINUTES",
                        "NOTIFICATION_REMINDER_FREQUENCY_MS");
    }
}
