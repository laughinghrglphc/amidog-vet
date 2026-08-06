package com.amidog.app.auth;

import com.amidog.app.support.PostgresIntegrationTest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TokenConsumptionPersistenceIntegrationTests extends PostgresIntegrationTest {

    private static final Instant CREATED_AT = Instant.parse("2026-07-29T00:00:00Z");
    private static final Instant NOW = CREATED_AT.plusSeconds(60);

    @Autowired
    UserAccountRepository users;

    @Autowired
    EmailVerificationTokenRepository emailVerificationTokens;

    @Autowired
    PasswordResetTokenRepository passwordResetTokens;

    @PersistenceContext
    EntityManager entityManager;

    @Test
    @Transactional
    void conditionallyConsumesAnEmailVerificationTokenOnlyOnce() {
        UserAccount user = user("ana@example.com");
        String hash = "a".repeat(64);
        emailVerificationTokens.saveAndFlush(EmailVerificationToken.create(
                user, hash, NOW.plusSeconds(1), CREATED_AT));

        assertThat(emailVerificationTokens.consumeIfActiveByTokenHash(hash, NOW)).isEqualTo(1);
        assertThat(emailVerificationTokens.consumeIfActiveByTokenHash(hash, NOW)).isZero();
    }

    @Test
    @Transactional
    void doesNotConsumeEmailVerificationTokensAtOrAfterExpiry() {
        UserAccount user = user("bea@example.com");
        emailVerificationTokens.saveAndFlush(EmailVerificationToken.create(
                user, "b".repeat(64), NOW, CREATED_AT));
        emailVerificationTokens.saveAndFlush(EmailVerificationToken.create(
                user, "c".repeat(64), NOW.minusMillis(1), CREATED_AT));

        assertThat(emailVerificationTokens.consumeIfActiveByTokenHash("b".repeat(64), NOW)).isZero();
        assertThat(emailVerificationTokens.consumeIfActiveByTokenHash("c".repeat(64), NOW)).isZero();
    }

    @Test
    @Transactional
    void conditionallyConsumesAPasswordResetTokenOnlyOnce() {
        UserAccount user = user("cami@example.com");
        String hash = "d".repeat(64);
        passwordResetTokens.saveAndFlush(PasswordResetToken.create(
                user, hash, NOW.plusSeconds(1), CREATED_AT));

        assertThat(passwordResetTokens.consumeIfActiveByTokenHash(hash, NOW)).isEqualTo(1);
        assertThat(passwordResetTokens.consumeIfActiveByTokenHash(hash, NOW)).isZero();
    }

    @Test
    @Transactional
    void doesNotConsumePasswordResetTokensAtOrAfterExpiry() {
        UserAccount user = user("dani@example.com");
        passwordResetTokens.saveAndFlush(PasswordResetToken.create(
                user, "e".repeat(64), NOW, CREATED_AT));
        passwordResetTokens.saveAndFlush(PasswordResetToken.create(
                user, "f".repeat(64), NOW.minusMillis(1), CREATED_AT));

        assertThat(passwordResetTokens.consumeIfActiveByTokenHash("e".repeat(64), NOW)).isZero();
        assertThat(passwordResetTokens.consumeIfActiveByTokenHash("f".repeat(64), NOW)).isZero();
    }

    @Test
    @Transactional
    void persistsVerificationAfterConditionallyConsumingAnEmailTokenInTheSameTransaction() {
        UserAccount user = user("elena@example.com");
        String hash = "g".repeat(64);
        emailVerificationTokens.saveAndFlush(EmailVerificationToken.create(
                user, hash, NOW.plusSeconds(1), CREATED_AT));

        assertThat(emailVerificationTokens.consumeIfActiveByTokenHash(hash, NOW)).isEqualTo(1);
        user.verify(NOW);
        entityManager.flush();
        entityManager.clear();

        assertThat(emailVerificationTokens.findByTokenHash(hash).orElseThrow().getConsumedAt()).isEqualTo(NOW);
        assertThat(users.findById(user.getId()).orElseThrow().getEmailVerifiedAt()).isEqualTo(NOW);
    }

    @Test
    @Transactional
    void persistsPasswordReplacementAfterConditionallyConsumingAResetTokenInTheSameTransaction() {
        UserAccount user = user("fer@example.com");
        String hash = "h".repeat(64);
        passwordResetTokens.saveAndFlush(PasswordResetToken.create(
                user, hash, NOW.plusSeconds(1), CREATED_AT));

        assertThat(passwordResetTokens.consumeIfActiveByTokenHash(hash, NOW)).isEqualTo(1);
        user.replacePassword("replacement-hash", NOW);
        entityManager.flush();
        entityManager.clear();

        assertThat(passwordResetTokens.findByTokenHash(hash).orElseThrow().getConsumedAt()).isEqualTo(NOW);
        assertThat(users.findById(user.getId()).orElseThrow().getPasswordHash()).isEqualTo("replacement-hash");
    }

    @Test
    @Transactional
    void emailCleanupPartitionsUnconsumedExpiryFromConsumedRetention() {
        UserAccount user = user("email-cleanup@example.com");
        EmailVerificationToken active = EmailVerificationToken.create(
                user, "i".repeat(64), NOW.plusMillis(1), CREATED_AT);
        EmailVerificationToken exactExpiry = EmailVerificationToken.create(
                user, "j".repeat(64), NOW, CREATED_AT);
        EmailVerificationToken recentConsumedExpired =
                EmailVerificationToken.create(
                        user, "k".repeat(64),
                        NOW.minusMillis(1), CREATED_AT);
        recentConsumedExpired.consume(CREATED_AT.plusSeconds(30));
        EmailVerificationToken newerThanCutoff =
                EmailVerificationToken.create(
                        user, "l".repeat(64),
                        NOW.plusSeconds(60),
                        NOW.minusSeconds(8 * 24 * 60 * 60));
        newerThanCutoff.consume(
                NOW.minusSeconds(7 * 24 * 60 * 60).plusMillis(1));
        EmailVerificationToken exactCutoff =
                EmailVerificationToken.create(
                        user, "m".repeat(64),
                        NOW.plusSeconds(60),
                        NOW.minusSeconds(8 * 24 * 60 * 60));
        exactCutoff.consume(NOW.minusSeconds(7 * 24 * 60 * 60));
        emailVerificationTokens.saveAllAndFlush(List.of(
                active,
                exactExpiry,
                recentConsumedExpired,
                newerThanCutoff,
                exactCutoff));

        emailVerificationTokens.deleteExpiredOrConsumedBefore(
                NOW.minusSeconds(7 * 24 * 60 * 60), NOW);

        assertThat(emailVerificationTokens.findByTokenHash("i".repeat(64)))
                .isPresent();
        assertThat(emailVerificationTokens.findByTokenHash("j".repeat(64)))
                .isEmpty();
        assertThat(emailVerificationTokens.findByTokenHash("k".repeat(64)))
                .isPresent();
        assertThat(emailVerificationTokens.findByTokenHash("l".repeat(64)))
                .isPresent();
        assertThat(emailVerificationTokens.findByTokenHash("m".repeat(64)))
                .isEmpty();
    }

    @Test
    @Transactional
    void resetCleanupPartitionsUnconsumedExpiryFromConsumedRetention() {
        UserAccount user = user("reset-cleanup@example.com");
        PasswordResetToken active = PasswordResetToken.create(
                user, "n".repeat(64), NOW.plusMillis(1), CREATED_AT);
        PasswordResetToken exactExpiry = PasswordResetToken.create(
                user, "o".repeat(64), NOW, CREATED_AT);
        PasswordResetToken recentConsumedExpired =
                PasswordResetToken.create(
                        user, "p".repeat(64),
                        NOW.minusMillis(1), CREATED_AT);
        recentConsumedExpired.consume(CREATED_AT.plusSeconds(30));
        PasswordResetToken newerThanCutoff =
                PasswordResetToken.create(
                        user, "q".repeat(64),
                        NOW.plusSeconds(60),
                        NOW.minusSeconds(8 * 24 * 60 * 60));
        newerThanCutoff.consume(
                NOW.minusSeconds(7 * 24 * 60 * 60).plusMillis(1));
        PasswordResetToken exactCutoff =
                PasswordResetToken.create(
                        user, "r".repeat(64),
                        NOW.plusSeconds(60),
                        NOW.minusSeconds(8 * 24 * 60 * 60));
        exactCutoff.consume(NOW.minusSeconds(7 * 24 * 60 * 60));
        passwordResetTokens.saveAllAndFlush(List.of(
                active,
                exactExpiry,
                recentConsumedExpired,
                newerThanCutoff,
                exactCutoff));

        passwordResetTokens.deleteExpiredOrConsumedBefore(
                NOW.minusSeconds(7 * 24 * 60 * 60), NOW);

        assertThat(passwordResetTokens.findByTokenHash("n".repeat(64)))
                .isPresent();
        assertThat(passwordResetTokens.findByTokenHash("o".repeat(64)))
                .isEmpty();
        assertThat(passwordResetTokens.findByTokenHash("p".repeat(64)))
                .isPresent();
        assertThat(passwordResetTokens.findByTokenHash("q".repeat(64)))
                .isPresent();
        assertThat(passwordResetTokens.findByTokenHash("r".repeat(64)))
                .isEmpty();
    }

    private UserAccount user(String email) {
        return users.saveAndFlush(UserAccount.client(email, "hash", CREATED_AT));
    }
}
