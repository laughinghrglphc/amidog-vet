package com.amidog.app.contact;

import com.amidog.app.common.api.ApiExceptionHandler;
import com.amidog.app.common.security.RateLimitService;
import com.amidog.app.common.text.PlainTextSanitizer;
import com.amidog.app.config.AmidogProperties;
import com.amidog.app.config.SecurityConfig;
import com.amidog.app.email.EmailMessage;
import com.amidog.app.email.EmailSender;
import com.amidog.app.support.RateLimitTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.mail.MailSendException;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ContactController.class)
@ContextConfiguration(classes = {
        ContactIntegrationTests.ContactTestApplication.class,
        ContactController.class
})
@EnableConfigurationProperties(AmidogProperties.class)
@Import({
        SecurityConfig.class,
        ApiExceptionHandler.class,
        RateLimitService.class,
        ContactService.class,
        PlainTextSanitizer.class,
        ContactIntegrationTests.EmailCaptureConfiguration.class
})
@TestPropertySource(properties = "amidog.contact.clinic-email=contacto@amidog.cl")
class ContactIntegrationTests {

    private static final String VALID_CONTACT = """
            {"name":"Ana Pérez","email":"ana@example.com",
             "message":"Quisiera consultar por una vacuna.","website":""}
            """;

    @Autowired MockMvc mvc;
    @Autowired CapturingEmailSender emailSender;
    @Autowired RateLimitService rateLimits;

    @BeforeEach
    void resetContactBoundary() {
        RateLimitTestSupport.reset(rateLimits);
        emailSender.clear();
    }

    @Test
    void sendsClinicInquiryAndUserAcknowledgementWithoutPersistingAMessage() throws Exception {
        mvc.perform(post("/api/v1/contact").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_CONTACT))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.message").value("Tu consulta fue enviada."));

        assertThat(emailSender.successfulHandoffs()).satisfiesExactly(
                clinic -> {
                    assertThat(clinic.to()).isEqualTo("contacto@amidog.cl");
                    assertThat(clinic.replyTo()).isEqualTo("ana@example.com");
                    assertThat(clinic.text()).contains(
                            "Ana Pérez",
                            "ana@example.com",
                            "Quisiera consultar");
                },
                acknowledgement -> {
                    assertThat(acknowledgement.to()).isEqualTo("ana@example.com");
                    assertThat(acknowledgement.replyTo()).isNull();
                });
    }

    @Test
    void silentlyAcceptsAHoneypotWithoutSendingMail() throws Exception {
        mvc.perform(post("/api/v1/contact").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Bot","email":"bot@example.com",
                                 "message":"Automated spam message",
                                 "website":"https://spam.invalid"}
                                """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.message").value("Tu consulta fue enviada."));

        assertThat(emailSender.successfulHandoffs()).isEmpty();
    }

    @Test
    void rejectsInvalidRawValuesWithoutSendingMail() throws Exception {
        mvc.perform(post("/api/v1/contact").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"A","email":"not-an-email",
                                 "message":"short","website":""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors.name").exists())
                .andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.errors.message").exists());

        assertThat(emailSender.successfulHandoffs()).isEmpty();
    }

    @Test
    void cleansPlainTextBeforeBuildingEmails() throws Exception {
        mvc.perform(post("/api/v1/contact").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"  Ana Pe\\u0301rez\\u0007  ",
                                 "email":"ana@example.com",
                                 "message":"  Quisiera\\u0000 consultar\\r\\npor una vacuna.  ",
                                 "website":""}
                                """))
                .andExpect(status().isAccepted());

        assertThat(emailSender.successfulHandoffs()).satisfiesExactly(
                clinic -> {
                    assertThat(clinic.text())
                            .contains("Ana Pérez", "Quisiera consultar\npor una vacuna.")
                            .doesNotContain("\u0000", "\u0007", "\r");
                    assertThat(clinic.replyTo()).isEqualTo("ana@example.com");
                },
                acknowledgement -> assertThat(acknowledgement.text())
                        .contains("Ana Pérez")
                        .doesNotContain("\u0007"));
    }

    @Test
    void rejectsValuesMadeInvalidByCleaning() throws Exception {
        mvc.perform(post("/api/v1/contact").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":" \\u0007 ","email":"ana@example.com",
                                 "message":"Quisiera consultar por una vacuna.",
                                 "website":""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message")
                        .value("La solicitud contiene datos inválidos."))
                .andExpect(jsonPath("$.errors.name").exists());

        assertThat(emailSender.successfulHandoffs()).isEmpty();
    }

    @Test
    void permitsFiveAcceptedSubmissionsPerRemoteAddressAndRejectsTheSixth()
            throws Exception {
        for (int attempt = 0; attempt < 5; attempt++) {
            mvc.perform(post("/api/v1/contact")
                            .with(csrf())
                            .with(request -> {
                                request.setRemoteAddr("198.51.100.24");
                                return request;
                            })
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_CONTACT))
                    .andExpect(status().isAccepted());
        }

        mvc.perform(post("/api/v1/contact")
                        .with(csrf())
                        .with(request -> {
                            request.setRemoteAddr("198.51.100.24");
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_CONTACT))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"))
                .andExpect(jsonPath("$.errors").isEmpty());

        assertThat(emailSender.successfulHandoffs()).hasSize(10);
    }

    @Test
    void stopsAfterFailedClinicHandoffWithControlledServiceUnavailable()
            throws Exception {
        emailSender.failOnAttempt(1);

        mvc.perform(post("/api/v1/contact").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_CONTACT))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code")
                        .value("EMAIL_DELIVERY_UNAVAILABLE"))
                .andExpect(jsonPath("$.message").value(
                        "No pudimos enviar tu consulta. Inténtalo nuevamente o usa WhatsApp."))
                .andExpect(jsonPath("$.errors").isEmpty());

        assertThat(emailSender.attemptedMessages()).satisfiesExactly(
                clinic -> assertThat(clinic.to())
                        .isEqualTo("contacto@amidog.cl"));
        assertThat(emailSender.successfulHandoffs()).isEmpty();
    }

    @Test
    void recordsFailedAcknowledgementAttemptAfterSuccessfulClinicHandoff()
            throws Exception {
        emailSender.failOnAttempt(2);

        mvc.perform(post("/api/v1/contact").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_CONTACT))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code")
                        .value("EMAIL_DELIVERY_UNAVAILABLE"))
                .andExpect(jsonPath("$.message").value(
                        "No pudimos enviar tu consulta. Inténtalo nuevamente o usa WhatsApp."))
                .andExpect(jsonPath("$.errors").isEmpty())
                .andExpect(content().string(
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(
                                "Quisiera consultar por una vacuna."))));

        assertThat(emailSender.attemptedMessages()).satisfiesExactly(
                clinic -> assertThat(clinic.to()).isEqualTo("contacto@amidog.cl"),
                acknowledgement -> assertThat(acknowledgement.to())
                        .isEqualTo("ana@example.com"));
        assertThat(emailSender.successfulHandoffs()).satisfiesExactly(
                clinic -> assertThat(clinic.to())
                        .isEqualTo("contacto@amidog.cl"));
    }

    @Test
    void remainsCsrfProtected() throws Exception {
        mvc.perform(post("/api/v1/contact")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_CONTACT))
                .andExpect(status().isForbidden());

        assertThat(emailSender.successfulHandoffs()).isEmpty();
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class ContactTestApplication {
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class EmailCaptureConfiguration {
        @Bean
        Clock clock() {
            return Clock.systemUTC();
        }

        @Bean
        UserDetailsService userDetailsService() {
            return new InMemoryUserDetailsManager(
                    User.withUsername("contact-test")
                            .password("{noop}unused")
                            .roles("CLIENT")
                            .build());
        }

        @Bean
        @Primary
        CapturingEmailSender capturingEmailSender() {
            return new CapturingEmailSender();
        }
    }

    static final class CapturingEmailSender implements EmailSender {
        private final List<EmailMessage> attemptedMessages =
                new CopyOnWriteArrayList<>();
        private final List<EmailMessage> successfulHandoffs =
                new CopyOnWriteArrayList<>();
        private final AtomicInteger attempts = new AtomicInteger();
        private volatile int failingAttempt = -1;

        @Override
        public void send(EmailMessage message) {
            attemptedMessages.add(message);
            if (attempts.incrementAndGet() == failingAttempt) {
                throw new MailSendException("mail transport unavailable");
            }
            successfulHandoffs.add(message);
        }

        List<EmailMessage> successfulHandoffs() {
            return List.copyOf(successfulHandoffs);
        }

        List<EmailMessage> attemptedMessages() {
            return List.copyOf(attemptedMessages);
        }

        void failOnAttempt(int attempt) {
            failingAttempt = attempt;
        }

        void clear() {
            attemptedMessages.clear();
            successfulHandoffs.clear();
            attempts.set(0);
            failingAttempt = -1;
        }
    }
}
