package com.amidog.app.auth;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@Component
@Transactional
public class ExpiredTokenCleanupJob {

    private static final Duration CONSUMED_TOKEN_RETENTION =
            Duration.ofDays(7);

    private final EmailVerificationTokenRepository verificationTokens;
    private final PasswordResetTokenRepository resetTokens;
    private final Clock clock;

    public ExpiredTokenCleanupJob(
            EmailVerificationTokenRepository verificationTokens,
            PasswordResetTokenRepository resetTokens,
            Clock clock) {
        this.verificationTokens = verificationTokens;
        this.resetTokens = resetTokens;
        this.clock = clock;
    }

    @Scheduled(
            cron = "${amidog.tokens.cleanup-cron:0 30 3 * * *}",
            zone = "${amidog.clinic-zone:America/Santiago}")
    public void runCleanup() {
        removeExpiredAndConsumedTokens();
    }

    public void removeExpiredAndConsumedTokens() {
        Instant now = clock.instant();
        Instant consumedCutoff =
                now.minus(CONSUMED_TOKEN_RETENTION);
        verificationTokens.deleteExpiredOrConsumedBefore(
                consumedCutoff, now);
        resetTokens.deleteExpiredOrConsumedBefore(
                consumedCutoff, now);
    }
}
