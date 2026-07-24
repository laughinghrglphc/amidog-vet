package com.amidog.app.auth;

import com.amidog.app.client.ClientRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@Service
class EmailDeliveryPreparation {

    private static final Duration VERIFICATION_TOKEN_LIFETIME = Duration.ofHours(24);
    private static final Duration RESET_TOKEN_LIFETIME = Duration.ofHours(1);

    private final EmailDeliveryJobRepository jobs;
    private final UserAccountRepository users;
    private final ClientRepository clients;
    private final EmailVerificationTokenRepository verificationTokens;
    private final PasswordResetTokenRepository resetTokens;
    private final SecureTokenService secureTokens;
    private final Clock clock;

    EmailDeliveryPreparation(
            EmailDeliveryJobRepository jobs,
            UserAccountRepository users,
            ClientRepository clients,
            EmailVerificationTokenRepository verificationTokens,
            PasswordResetTokenRepository resetTokens,
            SecureTokenService secureTokens,
            Clock clock
    ) {
        this.jobs = jobs;
        this.users = users;
        this.clients = clients;
        this.verificationTokens = verificationTokens;
        this.resetTokens = resetTokens;
        this.secureTokens = secureTokens;
        this.clock = clock;
    }

    @Transactional
    Object prepare(DeliveryAttempt attempt) {
        Instant now = clock.instant();
        // Reconciliation attempts carry a non-secret snapshot so retry preparation
        // can obey the global USER -> JOB -> TOKEN lock order.
        UserAccount user = users.findByIdForUpdate(attempt.userId()).orElse(null);
        if (user == null) {
            return null;
        }
        EmailDeliveryJob job = jobs.findByIdForUpdate(attempt.jobId()).orElse(null);
        if (job == null || !job.owns(attempt.fence(), now) || !job.represents(attempt)) {
            return null;
        }
        if (!isEligible(job, user) || !linkedTokenIsActive(job, user, now)) {
            // The row is already locked in this transaction. This exact-fence transition cannot
            // close a newer delivery attempt.
            job.complete(attempt.fence(), now);
            return null;
        }

        if (job.getDeliveryType() == EmailDeliveryType.VERIFICATION) {
            if (verificationTokens.consumeIfActiveByTokenHash(job.getTokenHash(), now) != 1) {
                job.complete(attempt.fence(), now);
                return null;
            }
            SecureTokenService.IssuedToken issued = secureTokens.issue();
            verificationTokens.save(EmailVerificationToken.create(
                    user, issued.hash(), now.plus(VERIFICATION_TOKEN_LIFETIME), now));
            if (!job.replaceTokenHash(attempt.fence(), attempt.tokenHash(), issued.hash(), now)) {
                throw new IllegalStateException("Delivery token fence changed while locked");
            }
            return new VerificationEmailRequested(
                    user.getId(), attempt.jobId(), attempt.fence(), issued.raw());
        }

        if (resetTokens.consumeIfActiveByTokenHash(job.getTokenHash(), now) != 1) {
            job.complete(attempt.fence(), now);
            return null;
        }
        SecureTokenService.IssuedToken issued = secureTokens.issue();
        resetTokens.save(PasswordResetToken.create(
                user, issued.hash(), now.plus(RESET_TOKEN_LIFETIME), now));
        if (!job.replaceTokenHash(attempt.fence(), attempt.tokenHash(), issued.hash(), now)) {
            throw new IllegalStateException("Delivery token fence changed while locked");
        }
        return new PasswordResetEmailRequested(
                user.getId(), attempt.jobId(), attempt.fence(), issued.raw());
    }

    private boolean linkedTokenIsActive(EmailDeliveryJob job, UserAccount user, Instant now) {
        if (job.getTokenHash() == null) {
            return false;
        }
        if (job.getDeliveryType() == EmailDeliveryType.VERIFICATION) {
            return verificationTokens.existsActiveForUser(job.getTokenHash(), user.getId(), now);
        }
        return resetTokens.existsActiveForUser(job.getTokenHash(), user.getId(), now);
    }

    private boolean isEligible(EmailDeliveryJob job, UserAccount user) {
        if (job.getDeliveryType() == EmailDeliveryType.PASSWORD_RESET) {
            return user.isEnabled() && user.isVerified();
        }
        return !user.isVerified()
                && user.getAccountType() == AccountType.CLIENT
                && clients.findByUserId(user.getId()).isPresent();
    }
}
