package com.amidog.app.auth;

import com.amidog.app.client.ClientRepository;
import com.amidog.app.config.AmidogProperties;
import com.amidog.app.email.EmailMessage;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class EmailDeliveryReconcilerTests {

    private static final Instant CREATED_AT = Instant.parse("2026-07-29T00:00:00Z");

    @Test
    void preparationAndListenerFailuresDoNotStopLaterClaimedJobs() {
        DeliveryAttempt preparationFailure = attempt(1L, "fence-1");
        DeliveryAttempt listenerFailure = attempt(2L, "fence-2");
        DeliveryAttempt succeeds = attempt(3L, "fence-3");
        ScriptedOutbox outbox = new ScriptedOutbox(List.of(
                preparationFailure, listenerFailure, succeeds));
        ScriptedPreparation preparation = new ScriptedPreparation() {
            @Override
            Object prepare(DeliveryAttempt attempt) {
                preparedJobIds.add(attempt.jobId());
                if (attempt.jobId().equals(1L)) {
                    throw new IllegalStateException("isolated preparation failure");
                }
                return new VerificationEmailRequested(
                        attempt.jobId(), attempt.jobId(), attempt.fence(), "raw-" + attempt.jobId());
            }
        };
        RecordingListener listener = new RecordingListener() {
            @Override
            public void sendVerificationEmail(VerificationEmailRequested event) {
                verificationJobIds.add(event.jobId());
                if (event.jobId().equals(2L)) {
                    throw new IllegalStateException("isolated listener failure");
                }
            }
        };

        new EmailDeliveryReconciler(listener, outbox, preparation).reconcile();

        assertThat(preparation.preparedJobIds).containsExactly(1L, 2L, 3L);
        assertThat(listener.verificationJobIds).containsExactly(2L, 3L);
        assertThat(outbox.claimCalls).isOne();
    }

    @Test
    void aClaimFailureDoesNotDisableFutureScheduledPolls() {
        DeliveryAttempt succeeds = attempt(9L, "fence-9");
        ScriptedOutbox outbox = new ScriptedOutbox(List.of(succeeds));
        outbox.failNextClaim = true;
        ScriptedPreparation preparation = new ScriptedPreparation() {
            @Override
            Object prepare(DeliveryAttempt attempt) {
                preparedJobIds.add(attempt.jobId());
                return new PasswordResetEmailRequested(
                        attempt.jobId(), attempt.jobId(), attempt.fence(), "raw");
            }
        };
        RecordingListener listener = new RecordingListener();
        EmailDeliveryReconciler reconciler = new EmailDeliveryReconciler(listener, outbox, preparation);

        reconciler.reconcile();
        reconciler.reconcile();

        assertThat(outbox.claimCalls).isEqualTo(2);
        assertThat(preparation.preparedJobIds).containsExactly(9L);
        assertThat(listener.resetJobIds).containsExactly(9L);
    }

    @Test
    void delayedOldCallbackCannotAffectTheCurrentReconciliationAttempt() {
        MutableClock clock = new MutableClock(CREATED_AT);
        UserAccount user = UserAccount.client("client@example.com", "hash", CREATED_AT);
        setField(user, "id", 31L);
        user.verify(CREATED_AT);
        EmailDeliveryJob job = EmailDeliveryJob.create(
                user, EmailDeliveryType.PASSWORD_RESET, "a".repeat(64), CREATED_AT);
        setField(job, "id", 71L);
        String oldFence = job.getDeliveryFence();

        EmailDeliveryJobRepository jobs = jobRepository(job);
        EmailDeliveryOutbox outbox = new EmailDeliveryOutbox(jobs, clock);
        AtomicInteger resetSaves = new AtomicInteger();
        PasswordResetTokenRepository resets = proxy(PasswordResetTokenRepository.class,
                (method, arguments) -> {
                    if (method.equals("existsActiveForUser")) {
                        return true;
                    }
                    if (method.equals("consumeIfActiveByTokenHash")) {
                        return 1;
                    }
                    if (method.equals("save")) {
                        resetSaves.incrementAndGet();
                        PasswordResetToken token = (PasswordResetToken) arguments[0];
                        assertThat(token.getTokenHash()).isEqualTo("b".repeat(64));
                        assertThat(token.getTokenHash()).doesNotContain("current-raw-token");
                        return token;
                    }
                    return defaultValue(method);
                });
        EmailDeliveryPreparation preparation = new EmailDeliveryPreparation(
                jobs,
                userRepository(user),
                emptyClients(),
                noOpVerificationTokens(),
                resets,
                new FixedTokenService(),
                clock);
        QueuedExecutor executor = new QueuedExecutor();
        List<EmailMessage> sent = new ArrayList<>();
        AccountEmailListener listener = new AccountEmailListener(
                userRepository(user), properties(), sent::add, executor, outbox);
        EmailDeliveryReconciler reconciler = new EmailDeliveryReconciler(
                listener, outbox, preparation);

        clock.instant = CREATED_AT.plusSeconds(61);
        reconciler.reconcile();
        String currentFence = job.getDeliveryFence();

        assertThat(currentFence).isNotEqualTo(oldFence);
        assertThat(resetSaves).hasValue(1);
        assertThat(executor.tasks).hasSize(1);
        assertThat(job.getState()).isEqualTo(EmailDeliveryJob.State.PROCESSING);

        clock.instant = CREATED_AT.plusSeconds(62);
        assertThat(outbox.delivered(job.getId(), oldFence)).isFalse();
        assertThat(outbox.failed(job.getId(), oldFence)).isFalse();
        assertThat(job.getState()).isEqualTo(EmailDeliveryJob.State.PROCESSING);
        assertThat(job.getDeliveryFence()).isEqualTo(currentFence);

        executor.runNext();

        assertThat(sent).singleElement().satisfies(message ->
                assertThat(message.text()).contains("current-raw-token"));
        assertThat(job.getState()).isEqualTo(EmailDeliveryJob.State.COMPLETED);
        assertThat(job.getDeliveryFence()).isEqualTo(currentFence);
    }

    private static EmailDeliveryJobRepository jobRepository(EmailDeliveryJob job) {
        return proxy(EmailDeliveryJobRepository.class, (method, arguments) -> {
            if (method.equals("findByIdForUpdate")) {
                return Optional.of(job);
            }
            if (method.equals("findDueIdsForUpdateSkipLocked")) {
                Instant now = (Instant) arguments[0];
                boolean pendingDue = job.getState() == EmailDeliveryJob.State.PENDING
                        && !job.getNextAttemptAt().isAfter(now);
                boolean leaseExpired = job.getState() == EmailDeliveryJob.State.PROCESSING
                        && job.getLeaseUntil() != null
                        && !job.getLeaseUntil().isAfter(now);
                return pendingDue || leaseExpired ? List.of(job.getId()) : List.of();
            }
            return defaultValue(method);
        });
    }

    private static DeliveryAttempt attempt(Long id, String fence) {
        return new DeliveryAttempt(id, fence, id, EmailDeliveryType.PASSWORD_RESET,
                Long.toHexString(id).repeat(64).substring(0, 64));
    }

    private static UserAccountRepository userRepository(UserAccount user) {
        return proxy(UserAccountRepository.class, (method, arguments) -> {
            if (method.equals("findByIdForUpdate") || method.equals("findById")) {
                return Optional.of(user);
            }
            return defaultValue(method);
        });
    }

    private static ClientRepository emptyClients() {
        return proxy(ClientRepository.class, (method, arguments) ->
                method.equals("findByUserId") ? Optional.empty() : defaultValue(method));
    }

    private static EmailVerificationTokenRepository noOpVerificationTokens() {
        return proxy(EmailVerificationTokenRepository.class,
                (method, arguments) -> method.equals("invalidateAllForUser")
                        ? 0
                        : defaultValue(method));
    }

    private static AmidogProperties properties() {
        return new AmidogProperties(
                ZoneId.of("America/Santiago"),
                URI.create("http://localhost:5173"),
                URI.create("http://localhost:5173"),
                new AmidogProperties.Booking(false, 30, 2, 90),
                new AmidogProperties.Admin("", "", "", ""),
                new AmidogProperties.Contact("", ""),
                new AmidogProperties.Email("log"));
    }

    private static void setField(Object target, String name, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, RepositoryCall call) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (proxy, method, arguments) -> call.invoke(method.getName(), arguments));
    }

    private static Object defaultValue(String method) {
        if (method.equals("toString")) {
            return "repository-proxy";
        }
        return null;
    }

    private static class ScriptedOutbox extends EmailDeliveryOutbox {
        private final List<DeliveryAttempt> attempts;
        private int claimCalls;
        private boolean failNextClaim;

        private ScriptedOutbox(List<DeliveryAttempt> attempts) {
            super(null, Clock.systemUTC());
            this.attempts = attempts;
        }

        @Override
        public List<DeliveryAttempt> claimDue() {
            claimCalls++;
            if (failNextClaim) {
                failNextClaim = false;
                throw new IllegalStateException("temporary claim failure");
            }
            return attempts;
        }
    }

    private abstract static class ScriptedPreparation extends EmailDeliveryPreparation {
        protected final List<Long> preparedJobIds = new ArrayList<>();

        private ScriptedPreparation() {
            super(null, null, null, null, null, null, Clock.systemUTC());
        }
    }

    private static class RecordingListener extends AccountEmailListener {
        protected final List<Long> verificationJobIds = new ArrayList<>();
        protected final List<Long> resetJobIds = new ArrayList<>();

        private RecordingListener() {
            super(null, properties(), message -> { }, Runnable::run);
        }

        @Override
        public void sendVerificationEmail(VerificationEmailRequested event) {
            verificationJobIds.add(event.jobId());
        }

        @Override
        public void sendPasswordResetEmail(PasswordResetEmailRequested event) {
            resetJobIds.add(event.jobId());
        }
    }

    private static final class FixedTokenService extends SecureTokenService {
        @Override
        public IssuedToken issue() {
            return new IssuedToken("current-raw-token", "b".repeat(64));
        }
    }

    private static final class QueuedExecutor implements Executor {
        private final Deque<Runnable> tasks = new ArrayDeque<>();

        @Override
        public void execute(Runnable command) {
            tasks.addLast(command);
        }

        private void runNext() {
            tasks.removeFirst().run();
        }
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }

    @FunctionalInterface
    private interface RepositoryCall {
        Object invoke(String method, Object[] arguments);
    }
}
