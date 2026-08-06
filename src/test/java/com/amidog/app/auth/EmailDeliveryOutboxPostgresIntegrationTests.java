package com.amidog.app.auth;

import com.amidog.app.client.Client;
import com.amidog.app.client.ClientRepository;
import com.amidog.app.support.PostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@TestPropertySource(properties = "amidog.email.reconciliation-delay-ms=3600000")
class EmailDeliveryOutboxPostgresIntegrationTests extends PostgresIntegrationTest {

    @Autowired
    EmailDeliveryOutbox outbox;

    @Autowired
    EmailDeliveryPreparation preparation;

    @Autowired
    EmailDeliveryJobRepository jobs;

    @Autowired
    UserAccountRepository users;

    @Autowired
    ClientRepository clients;

    @Autowired
    EmailVerificationTokenRepository verificationTokens;

    @Autowired
    PasswordResetTokenRepository resetTokens;

    @Autowired
    SecureTokenService secureTokens;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PlatformTransactionManager transactionManager;

    @BeforeEach
    void clearAccountData() {
        jdbc.execute("""
                truncate table email_delivery_jobs, email_verification_tokens,
                    password_reset_tokens, clients, users restart identity cascade
                """);
    }

    @Test
    void twoWorkersUseSkipLockedWithoutDoubleClaimingTheSameDueJob() throws Exception {
        UserAccount user = verifiedClient("workers@example.com");
        DeliveryAttempt original = outbox.enqueue(user, EmailDeliveryType.PASSWORD_RESET, "1".repeat(64));
        jdbc.update("""
                update email_delivery_jobs
                set state = 'PENDING', next_attempt_at = clock_timestamp() - interval '1 second',
                    lease_until = null
                where id = ?
                """, original.jobId());

        CountDownLatch firstWorkerLocked = new CountDownLatch(1);
        CountDownLatch releaseFirstWorker = new CountDownLatch(1);
        TransactionTemplate transactions = new TransactionTemplate(transactionManager);
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            Future<List<Long>> first = workers.submit(() -> transactions.execute(status -> {
                List<Long> ids = jobs.findDueIdsForUpdateSkipLocked(Instant.now());
                assertThat(ids).containsExactly(original.jobId());
                EmailDeliveryJob locked = jobs.findByIdForUpdate(ids.getFirst()).orElseThrow();
                assertThat(locked.claim(Instant.now())).isTrue();
                firstWorkerLocked.countDown();
                await(releaseFirstWorker);
                return ids;
            }));

            assertThat(firstWorkerLocked.await(5, TimeUnit.SECONDS)).isTrue();
            Future<List<Long>> second = workers.submit(() -> transactions.execute(status ->
                    jobs.findDueIdsForUpdateSkipLocked(Instant.now())));

            assertThat(second.get(5, TimeUnit.SECONDS)).isEmpty();
            releaseFirstWorker.countDown();
            assertThat(first.get(5, TimeUnit.SECONDS)).containsExactly(original.jobId());
        } finally {
            releaseFirstWorker.countDown();
            workers.shutdownNow();
            assertThat(workers.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }

        EmailDeliveryJob claimed = jobs.findById(original.jobId()).orElseThrow();
        assertThat(claimed.getState()).isEqualTo(EmailDeliveryJob.State.PROCESSING);
        assertThat(claimed.getDeliveryFence()).isNotEqualTo(original.fence());
    }

    @Test
    void expiredLeaseIsReclaimableWithANewFence() {
        UserAccount user = verifiedClient("expired@example.com");
        DeliveryAttempt original = outbox.enqueue(user, EmailDeliveryType.PASSWORD_RESET, "2".repeat(64));
        jdbc.update("""
                update email_delivery_jobs
                set lease_until = clock_timestamp() - interval '1 second'
                where id = ?
                """, original.jobId());

        List<DeliveryAttempt> reclaimed = outbox.claimDue();

        assertThat(reclaimed).singleElement().satisfies(attempt -> {
            assertThat(attempt.jobId()).isEqualTo(original.jobId());
            assertThat(attempt.fence()).isNotEqualTo(original.fence());
        });
        EmailDeliveryJob persisted = jobs.findById(original.jobId()).orElseThrow();
        assertThat(persisted.getState()).isEqualTo(EmailDeliveryJob.State.PROCESSING);
        assertThat(persisted.getLeaseUntil()).isAfter(Instant.now());
    }

    @Test
    void staleDatabaseCallbacksAreNoOpsAgainstANewerLeaseFence() {
        UserAccount user = verifiedClient("callbacks@example.com");
        DeliveryAttempt original = outbox.enqueue(user, EmailDeliveryType.PASSWORD_RESET, "3".repeat(64));
        jdbc.update("""
                update email_delivery_jobs
                set lease_until = clock_timestamp() - interval '1 second'
                where id = ?
                """, original.jobId());
        DeliveryAttempt current = outbox.claimDue().getFirst();

        assertThat(outbox.delivered(original.jobId(), original.fence())).isFalse();
        assertThat(outbox.failed(original.jobId(), original.fence())).isFalse();

        EmailDeliveryJob afterStaleCallbacks = jobs.findById(original.jobId()).orElseThrow();
        assertThat(afterStaleCallbacks.getState()).isEqualTo(EmailDeliveryJob.State.PROCESSING);
        assertThat(afterStaleCallbacks.getDeliveryFence()).isEqualTo(current.fence());
        assertThat(afterStaleCallbacks.getAttempts()).isZero();

        assertThat(outbox.delivered(current.jobId(), current.fence())).isTrue();
        assertThat(jobs.findById(current.jobId()).orElseThrow().getState())
                .isEqualTo(EmailDeliveryJob.State.COMPLETED);
    }

    @Test
    void eligibilityChangesCloseCurrentJobsWithoutCreatingNewTokens() {
        UserAccount resetUser = verifiedClient("disabled@example.com");
        DeliveryAttempt resetAttempt = outbox.enqueue(
                resetUser, EmailDeliveryType.PASSWORD_RESET, "4".repeat(64));
        jdbc.update("update users set enabled = false where id = ?", resetUser.getId());

        assertThat(preparation.prepare(resetAttempt)).isNull();

        EmailDeliveryJob resetJob = jobs.findById(resetAttempt.jobId()).orElseThrow();
        assertThat(resetJob.getState()).isEqualTo(EmailDeliveryJob.State.COMPLETED);
        assertThat(resetJob.getDeliveryFence()).isEqualTo(resetAttempt.fence());
        assertThat(resetTokens.count()).isZero();

        UserAccount verificationUser = users.saveAndFlush(UserAccount.client(
                "verified-before-retry@example.com", "hash", Instant.now()));
        clients.saveAndFlush(Client.create(
                verificationUser, "Verification Client", "+56900000001", Instant.now()));
        DeliveryAttempt verificationAttempt = outbox.enqueue(
                verificationUser, EmailDeliveryType.VERIFICATION, "5".repeat(64));
        jdbc.update("""
                update users
                set enabled = true, email_verified_at = clock_timestamp(),
                    updated_at = clock_timestamp()
                where id = ?
                """, verificationUser.getId());

        assertThat(preparation.prepare(verificationAttempt)).isNull();

        EmailDeliveryJob verificationJob = jobs.findById(verificationAttempt.jobId()).orElseThrow();
        assertThat(verificationJob.getState()).isEqualTo(EmailDeliveryJob.State.COMPLETED);
        assertThat(verificationJob.getDeliveryFence()).isEqualTo(verificationAttempt.fence());
        assertThat(verificationTokens.count()).isZero();
    }

    @Test
    void activeLinkedRequestReissuesAndMovesExactJobFenceWithoutPersistingRawToken() {
        UserAccount user = verifiedClient("active-reissue@example.com");
        Instant now = Instant.now();
        SecureTokenService.IssuedToken original = secureTokens.issue();
        resetTokens.saveAndFlush(PasswordResetToken.create(
                user, original.hash(), now.plusSeconds(3600), now));
        DeliveryAttempt first = outbox.enqueue(
                user, EmailDeliveryType.PASSWORD_RESET, original.hash());
        assertThat(outbox.failed(first.jobId(), first.fence())).isTrue();
        makeDue(first.jobId());
        DeliveryAttempt retry = outbox.claimDue().getFirst();

        Object prepared = preparation.prepare(retry);

        assertThat(prepared).isInstanceOf(PasswordResetEmailRequested.class);
        PasswordResetEmailRequested event = (PasswordResetEmailRequested) prepared;
        EmailDeliveryJob job = jobs.findById(first.jobId()).orElseThrow();
        assertThat(job.getTokenHash()).isNotEqualTo(original.hash());
        assertThat(job.getTokenHash()).hasSize(64).doesNotContain(event.rawToken());
        assertThat(resetTokens.findByTokenHash(original.hash()).orElseThrow().getConsumedAt())
                .isNotNull();
        assertThat(resetTokens.findByTokenHash(job.getTokenHash()).orElseThrow().canConsume(Instant.now()))
                .isTrue();
        assertThat(jdbc.queryForObject("""
                select count(*) from email_delivery_jobs
                where token_hash = ? or token_hash = ?
                """, Integer.class, event.rawToken(), original.raw())).isZero();
    }

    @Test
    void oldFailedJobCannotInvalidateOrReplaceANewerRequest() {
        UserAccount user = verifiedClient("superseded@example.com");
        Instant now = Instant.now();
        SecureTokenService.IssuedToken oldToken = secureTokens.issue();
        resetTokens.saveAndFlush(PasswordResetToken.create(
                user, oldToken.hash(), now.plusSeconds(3600), now));
        DeliveryAttempt oldJob = outbox.enqueue(
                user, EmailDeliveryType.PASSWORD_RESET, oldToken.hash());
        assertThat(outbox.failed(oldJob.jobId(), oldJob.fence())).isTrue();

        resetTokens.invalidateAllForUser(user.getId(), now.plusMillis(1));
        SecureTokenService.IssuedToken currentToken = secureTokens.issue();
        UserAccount currentOwner = users.getReferenceById(user.getId());
        resetTokens.saveAndFlush(PasswordResetToken.create(
                currentOwner, currentToken.hash(), now.plusSeconds(3600), now.plusMillis(1)));
        DeliveryAttempt currentJob = outbox.enqueue(
                currentOwner, EmailDeliveryType.PASSWORD_RESET, currentToken.hash());
        makeDue(oldJob.jobId());

        DeliveryAttempt reclaimedOld = outbox.claimDue().stream()
                .filter(attempt -> attempt.jobId().equals(oldJob.jobId()))
                .findFirst().orElseThrow();
        assertThat(preparation.prepare(reclaimedOld)).isNull();

        assertThat(jobs.findById(oldJob.jobId()).orElseThrow().getState())
                .isEqualTo(EmailDeliveryJob.State.COMPLETED);
        assertThat(jobs.findById(currentJob.jobId()).orElseThrow().getTokenHash())
                .isEqualTo(currentToken.hash());
        assertThat(resetTokens.findByTokenHash(currentToken.hash()).orElseThrow()
                .canConsume(Instant.now())).isTrue();
    }

    @Test
    void acknowledgementFailureAfterConsumptionAndExactExpiryCloseWithoutNewLink() {
        UserAccount user = verifiedClient("consumed@example.com");
        Instant now = Instant.now();
        SecureTokenService.IssuedToken issued = secureTokens.issue();
        resetTokens.saveAndFlush(PasswordResetToken.create(
                user, issued.hash(), now.plusSeconds(3600), now));
        DeliveryAttempt job = outbox.enqueue(
                user, EmailDeliveryType.PASSWORD_RESET, issued.hash());

        assertThat(resetTokens.consumeIfActiveByTokenHash(issued.hash(), Instant.now())).isOne();
        jdbc.update("""
                update email_delivery_jobs
                set lease_until = clock_timestamp() - interval '1 second'
                where id = ?
                """, job.jobId());
        DeliveryAttempt retry = outbox.claimDue().getFirst();

        assertThat(preparation.prepare(retry)).isNull();
        assertThat(jobs.findById(job.jobId()).orElseThrow().getState())
                .isEqualTo(EmailDeliveryJob.State.COMPLETED);
        assertThat(resetTokens.count()).isOne();

        UserAccount expiryUser = verifiedClient("expiry@example.com");
        SecureTokenService.IssuedToken expiring = secureTokens.issue();
        resetTokens.saveAndFlush(PasswordResetToken.create(
                expiryUser, expiring.hash(), Instant.now().plusSeconds(30), Instant.now()));
        DeliveryAttempt expiryJob = outbox.enqueue(
                expiryUser, EmailDeliveryType.PASSWORD_RESET, expiring.hash());
        jdbc.update("update password_reset_tokens set expires_at = clock_timestamp() where token_hash = ?",
                expiring.hash());
        jdbc.update("""
                update email_delivery_jobs
                set lease_until = clock_timestamp() - interval '1 second'
                where id = ?
                """, expiryJob.jobId());
        DeliveryAttempt expiryRetry = outbox.claimDue().stream()
                .filter(attempt -> attempt.jobId().equals(expiryJob.jobId()))
                .findFirst().orElseThrow();

        assertThat(preparation.prepare(expiryRetry)).isNull();
        assertThat(jobs.findById(expiryJob.jobId()).orElseThrow().getState())
                .isEqualTo(EmailDeliveryJob.State.COMPLETED);
        assertThat(resetTokens.count()).isEqualTo(2);
    }

    @Test
    void databaseRejectsInvalidActiveHashesAndAllowsOnlyCompletedLegacyNull() {
        UserAccount user = verifiedClient("constraint@example.com");
        for (String sqlHash : List.of(
                "null",
                "'" + "a".repeat(63) + "'",
                "'" + "A".repeat(64) + "'",
                "'" + "g".repeat(64) + "'")) {
            assertThatThrownBy(() -> jdbc.update("""
                    insert into email_delivery_jobs(
                        user_id, delivery_type, state, attempts, next_attempt_at,
                        lease_until, delivery_fence, token_hash)
                    values (?, 'PASSWORD_RESET', 'PENDING', 0, now(), null, ?, %s)
                    """.formatted(sqlHash), user.getId(),
                    java.util.UUID.randomUUID().toString()))
                    .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class)
                    .hasRootCauseInstanceOf(java.sql.SQLException.class);
        }

        jdbc.update("""
                insert into email_delivery_jobs(
                    user_id, delivery_type, state, attempts, next_attempt_at,
                    lease_until, delivery_fence, token_hash, completed_at)
                values (?, 'PASSWORD_RESET', 'COMPLETED', 0, now(), null, ?, null, now())
                """, user.getId(), java.util.UUID.randomUUID().toString());

        assertThat(jobs.findDueIdsForUpdateSkipLocked(Instant.now().plusSeconds(3600)))
                .isEmpty();
        assertThat(jdbc.queryForObject("""
                select count(*) from email_delivery_jobs
                where state='COMPLETED' and token_hash is null
                """, Integer.class)).isOne();
    }

    private void makeDue(Long jobId) {
        jdbc.update("""
                update email_delivery_jobs
                set state = 'PENDING',
                    next_attempt_at = clock_timestamp() - interval '1 second',
                    lease_until = null
                where id = ?
                """, jobId);
    }

    private UserAccount verifiedClient(String email) {
        Instant now = Instant.now();
        UserAccount user = UserAccount.client(email, "hash", now);
        user.verify(now);
        user = users.saveAndFlush(user);
        clients.saveAndFlush(Client.create(user, "Client", "+56900000000", now));
        return user;
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("coordination latch timed out");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("worker interrupted", exception);
        }
    }
}
