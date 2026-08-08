package com.amidog.app.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class EmailDeliveryReconciler {

    private static final Logger log = LoggerFactory.getLogger(EmailDeliveryReconciler.class);

    private final AccountEmailListener listener;
    private final EmailDeliveryOutbox outbox;
    private final EmailDeliveryPreparation preparation;

    public EmailDeliveryReconciler(
            AccountEmailListener listener,
            EmailDeliveryOutbox outbox,
            EmailDeliveryPreparation preparation
    ) {
        this.listener = listener;
        this.outbox = outbox;
        this.preparation = preparation;
    }

    @Scheduled(fixedDelayString = "${amidog.email.reconciliation-delay-ms:30000}")
    public void reconcile() {
        List<DeliveryAttempt> attempts;
        try {
            attempts = outbox.claimDue();
        } catch (RuntimeException exception) {
            // A database outage in this invocation must not suppress later scheduled polls.
            log.warn("Email delivery reconciliation could not claim due jobs");
            return;
        }

        for (DeliveryAttempt attempt : attempts) {
            try {
                Object event = preparation.prepare(attempt);
                if (event instanceof VerificationEmailRequested verification) {
                    listener.sendVerificationEmail(verification);
                } else if (event instanceof PasswordResetEmailRequested reset) {
                    listener.sendPasswordResetEmail(reset);
                }
            } catch (RuntimeException exception) {
                // The durable lease becomes reclaimable. Continue so one bad job cannot starve
                // other jobs in this batch.
                log.warn("Email delivery reconciliation failed for jobId={}", attempt.jobId());
            }
        }
    }
}
