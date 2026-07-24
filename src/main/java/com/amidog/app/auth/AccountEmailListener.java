package com.amidog.app.auth;

import com.amidog.app.config.AmidogProperties;
import com.amidog.app.email.EmailMessage;
import com.amidog.app.email.EmailSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

@Component
public class AccountEmailListener {

    private static final Logger log = LoggerFactory.getLogger(AccountEmailListener.class);
    private static final String VERIFICATION_SUBJECT = "Verifica tu cuenta AmiDog";
    private static final String PASSWORD_RESET_SUBJECT = "Restablece tu contrase\u00f1a de AmiDog";

    private final UserAccountRepository users;
    private final AmidogProperties properties;
    private final EmailSender emailSender;
    private final Executor emailDeliveryExecutor;
    private final EmailDeliveryOutbox outbox;

    @Autowired
    public AccountEmailListener(UserAccountRepository users, AmidogProperties properties, EmailSender emailSender,
                                @Qualifier("emailDeliveryExecutor") Executor emailDeliveryExecutor, EmailDeliveryOutbox outbox) {
        this.users = users;
        this.properties = properties;
        this.emailSender = emailSender;
        this.emailDeliveryExecutor = emailDeliveryExecutor;
        this.outbox = outbox;
    }
    AccountEmailListener(UserAccountRepository users, AmidogProperties properties, EmailSender emailSender, Executor executor) {
        this(users, properties, emailSender, executor, null);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void sendVerificationEmail(VerificationEmailRequested event) {
        enqueue("verification", event.jobId(), event.fence(), () -> deliverVerificationEmail(event));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void sendPasswordResetEmail(PasswordResetEmailRequested event) {
        enqueue("password reset", event.jobId(), event.fence(), () -> deliverPasswordResetEmail(event));
    }

    private void enqueue(String type, Long jobId, String fence, Runnable task) {
        try {
            emailDeliveryExecutor.execute(task);
        } catch (RejectedExecutionException ignored) {
            retryDelivery(jobId, fence);
            // The raw token remains only in the rejected Runnable and is never logged.
            log.warn("Post-commit {} email delivery queue is saturated; delivery was not scheduled", type);
        }
    }

    private void deliverVerificationEmail(VerificationEmailRequested event) {
        String recipient = "unknown";
        try {
            UserAccount user = users.findById(event.userId()).orElseThrow();
            recipient = user.getEmailNormalized();
            URI verifyUrl = properties.frontendBaseUrl().resolve(
                    "/verificar-correo?token=" + URLEncoder.encode(event.rawToken(), StandardCharsets.UTF_8));
            emailSender.send(new EmailMessage(
                    recipient,
                    VERIFICATION_SUBJECT,
                    "Verifica tu cuenta usando este enlace: " + verifyUrl,
                    null));
            completeDelivery(event.jobId(), event.fence());
        } catch (RuntimeException ignored) {
            retryDelivery(event.jobId(), event.fence());
            log.warn("Verification email delivery failed after commit: recipient={}, subject={}",
                    recipient, VERIFICATION_SUBJECT);
        }
    }

    private void deliverPasswordResetEmail(PasswordResetEmailRequested event) {
        String recipient = "unknown";
        try {
            UserAccount user = users.findById(event.userId()).orElseThrow();
            recipient = user.getEmailNormalized();
            URI resetUrl = properties.frontendBaseUrl().resolve(
                    "/restablecer-contrasena?token=" + URLEncoder.encode(event.rawToken(), StandardCharsets.UTF_8));
            emailSender.send(new EmailMessage(
                    recipient,
                    PASSWORD_RESET_SUBJECT,
                    "Restablece tu contrase\u00f1a usando este enlace: " + resetUrl,
                    null));
            completeDelivery(event.jobId(), event.fence());
        } catch (RuntimeException ignored) {
            retryDelivery(event.jobId(), event.fence());
            log.warn("Password reset email delivery failed after commit: recipient={}, subject={}",
                    recipient, PASSWORD_RESET_SUBJECT);
        }
    }

    private void completeDelivery(Long jobId, String fence) {
        if (outbox == null || jobId == null) {
            return;
        }
        try {
            outbox.delivered(jobId, fence);
        } catch (RuntimeException ignored) {
            log.warn("Email delivery acknowledgement could not be persisted for jobId={}", jobId);
        }
    }

    private void retryDelivery(Long jobId, String fence) {
        if (outbox == null || jobId == null) {
            return;
        }
        try {
            outbox.failed(jobId, fence);
        } catch (RuntimeException ignored) {
            // The PROCESSING lease remains reclaimable even if this retry update cannot be saved.
            log.warn("Email delivery retry could not be persisted for jobId={}", jobId);
        }
    }
}

record VerificationEmailRequested(Long userId, Long jobId, String fence, String rawToken) {
    VerificationEmailRequested(Long userId, String rawToken) { this(userId, null, null, rawToken); }
    @Override
    public String toString() {
        return "VerificationEmailRequested[userId=" + userId + ", jobId=" + jobId + ", rawToken=redacted]";
    }
}
