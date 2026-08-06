package com.amidog.app.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.SimpleMailMessage;
import com.amidog.app.email.EmailMessage;
import com.amidog.app.email.SmtpEmailSender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SmtpTransportConfigurationTests {

    @Test
    void productionConfigurationBindingCreatesNestedSmtpSettings() {
        new ApplicationContextRunner()
                .withUserConfiguration(PropertiesBindingConfiguration.class)
                .withPropertyValues(
                        "amidog.clinic-zone=America/Santiago",
                        "amidog.frontend-base-url=http://127.0.0.1:5173",
                        "amidog.frontend-origin=http://127.0.0.1:5173",
                        "amidog.booking.auto-confirm=false",
                        "amidog.booking.duration-minutes=30",
                        "amidog.booking.minimum-notice-hours=2",
                        "amidog.booking.horizon-days=90",
                        "amidog.email.delivery=smtp",
                        "amidog.email.smtp.host=127.0.0.1",
                        "amidog.email.smtp.port=1025",
                        "amidog.email.smtp.auth=false",
                        "amidog.email.smtp.starttls=false",
                        "amidog.email.smtp.starttls-required=false",
                        "amidog.email.smtp.ssl=false",
                        "amidog.email.smtp.allow-plaintext=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    AmidogProperties properties = context.getBean(AmidogProperties.class);
                    assertThat(properties.email()).isNotNull();
                    assertThat(properties.email().delivery()).isEqualTo("smtp");
                    assertThat(properties.email().smtp().host()).isEqualTo("127.0.0.1");
                    assertThat(properties.email().smtp().port()).isEqualTo(1025);
                });
    }

    @Test
    void startTls587BuildsProviderNeutralSecureJavaMailProperties() {
        AmidogProperties.Smtp settings = smtp("smtp.example.com", 587,
                true, true, true, false, false, "user", "secret");
        settings.validateForDelivery();

        JavaMailSenderImpl sender = (JavaMailSenderImpl) new SmtpTransportConfiguration()
                .javaMailSender(properties(settings));

        assertThat(sender.getHost()).isEqualTo("smtp.example.com");
        assertThat(sender.getPort()).isEqualTo(587);
        assertThat(sender.getJavaMailProperties())
                .containsEntry("mail.smtp.auth", "true")
                .containsEntry("mail.smtp.starttls.enable", "true")
                .containsEntry("mail.smtp.starttls.required", "true")
                .containsEntry("mail.smtp.ssl.enable", "false")
                .containsEntry("mail.smtp.ssl.checkserveridentity", "true");
        assertThat(sender.toString()).doesNotContain("secret");
        assertThat(settings.toString()).doesNotContain("username=user", "secret");
    }

    @Test
    void implicitSsl465IsSupported() {
        AmidogProperties.Smtp settings = smtp("smtp.example.com", 465,
                true, false, false, true, false, "user", "secret");
        settings.validateForDelivery();
        assertThat(SmtpTransportConfiguration.javaMailProperties(settings))
                .containsEntry("mail.smtp.ssl.enable", "true")
                .containsEntry("mail.smtp.starttls.enable", "false");
    }

    @Test
    void localPlaintextRequiresExplicitOptInAndNoAuthentication() {
        AmidogProperties.Smtp local = smtp("localhost", 1025,
                false, false, false, false, true, "", "");
        local.validateForDelivery();
        assertThat(SmtpTransportConfiguration.javaMailProperties(local))
                .containsEntry("mail.smtp.auth", "false");
        JavaMailSenderImpl sender = (JavaMailSenderImpl) new SmtpTransportConfiguration()
                .javaMailSender(properties(local));
        assertThat(sender.getUsername()).isNull();
        assertThat(sender.getPassword()).isNull();

        assertThatThrownBy(() -> smtp("localhost", 1025,
                false, false, false, false, false, "", "").validateForDelivery())
                .hasMessageContaining("SMTP_ALLOW_PLAINTEXT");
        assertThatThrownBy(() -> smtp("mail.example.com", 25,
                false, false, false, false, true, "", "").validateForDelivery())
                .hasMessageContaining("required STARTTLS");
    }

    @Test
    void noAuthRejectsAnyCredentialAndNeverPopulatesSenderCredentials() {
        assertThatThrownBy(() -> smtp("localhost", 1025,
                false, false, false, false, true, "unexpected", "")
                .validateForDelivery()).hasMessageContaining("must be blank");
        assertThatThrownBy(() -> smtp("localhost", 1025,
                false, false, false, false, true, "", "unexpected")
                .validateForDelivery()).hasMessageContaining("must be blank");
    }

    @Test
    void remoteOrAuthenticatedStartTlsCannotDowngrade() {
        assertThatThrownBy(() -> smtp("smtp.example.com", 587,
                true, true, false, false, false, "user", "secret")
                .validateForDelivery()).hasMessageContaining("required STARTTLS");
        assertThatThrownBy(() -> smtp("smtp.example.com", 587,
                false, true, false, false, false, "", "")
                .validateForDelivery()).hasMessageContaining("required STARTTLS");
        assertThatThrownBy(() -> smtp("localhost", 587,
                true, true, false, false, true, "user", "secret")
                .validateForDelivery()).hasMessageContaining("required STARTTLS");
    }

    @Test
    void rejectsContradictoryOrIncompleteSecureModes() {
        assertThatThrownBy(() -> smtp("smtp.example.com", 587,
                true, true, true, true, false, "user", "secret").validateForDelivery())
                .hasMessageContaining("mutually exclusive");
        assertThatThrownBy(() -> smtp("smtp.example.com", 587,
                true, false, true, false, false, "user", "secret").validateForDelivery())
                .hasMessageContaining("requires SMTP_STARTTLS");
        assertThatThrownBy(() -> smtp("smtp.example.com", 587,
                true, true, true, false, false, "", "").validateForDelivery())
                .hasMessageContaining("SMTP_USERNAME");
        assertThatThrownBy(() -> smtp("", 587,
                false, true, true, false, false, "", "").validateForDelivery())
                .hasMessageContaining("SMTP_HOST");
        assertThatThrownBy(() -> smtp("smtp.example.com", 0,
                false, true, true, false, false, "", "").validateForDelivery())
                .hasMessageContaining("SMTP_PORT");
    }

    @Test
    void logDeliveryDoesNotRequireAnySmtpConfiguration() {
        AmidogProperties properties = properties(null);
        assertThat(properties.email().delivery()).isEqualTo("log");
        assertThat(properties.email().smtp()).isNull();
    }

    @Test
    void smtpAdapterMapsApplicationMessageWithoutLoggingOrChangingBody() {
        class RecordingSender extends JavaMailSenderImpl {
            private SimpleMailMessage captured;
            @Override
            public void send(SimpleMailMessage... messages) {
                captured = messages[0];
            }
        }
        RecordingSender sender = new RecordingSender();

        new SmtpEmailSender(sender).send(new EmailMessage(
                "ana@example.com", "Restablece tu contrase\u00f1a",
                "Enlace de un solo uso", "contacto@example.com"));

        assertThat(sender.captured.getTo()).containsExactly("ana@example.com");
        assertThat(sender.captured.getSubject()).isEqualTo("Restablece tu contrase\u00f1a");
        assertThat(sender.captured.getText()).isEqualTo("Enlace de un solo uso");
        assertThat(sender.captured.getReplyTo()).isEqualTo("contacto@example.com");
    }

    private static AmidogProperties properties(AmidogProperties.Smtp smtp) {
        return new AmidogProperties(
                java.time.ZoneId.of("America/Santiago"),
                java.net.URI.create("http://localhost:5173"),
                java.net.URI.create("http://localhost:5173"),
                new AmidogProperties.Booking(false, 30, 2, 90),
                new AmidogProperties.Admin("", "", "", ""),
                new AmidogProperties.Contact("", ""),
                new AmidogProperties.Email(smtp == null ? "log" : "smtp", smtp));
    }

    private static AmidogProperties.Smtp smtp(
            String host, int port, boolean auth, boolean starttls, boolean required,
            boolean ssl, boolean plaintext, String username, String password
    ) {
        return new AmidogProperties.Smtp(
                host, port, username, password, auth, starttls, required, ssl, plaintext);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(AmidogProperties.class)
    static class PropertiesBindingConfiguration {
    }
}
