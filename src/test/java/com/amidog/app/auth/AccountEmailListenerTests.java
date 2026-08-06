package com.amidog.app.auth;

import com.amidog.app.config.AmidogProperties;
import com.amidog.app.email.EmailSender;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.event.EventListenerMethodProcessor;
import org.springframework.transaction.event.TransactionalEventListenerFactory;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.lang.reflect.Proxy;
import java.lang.reflect.Field;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class AccountEmailListenerTests {

    @Test
    void swallowsAfterCommitDeliveryFailureInsteadOfRewritingCommittedRequestOutcome() {
        UserAccount user = UserAccount.client("ana@example.com", "hash", Instant.parse("2026-07-29T00:00:00Z"));
        UserAccountRepository users = repositoryReturning(user);
        EmailSender failingSender = message -> {
            throw new IllegalStateException("smtp unavailable");
        };
        AccountEmailListener listener = new AccountEmailListener(users, properties(), failingSender, Runnable::run);

        assertThatCode(() -> listener.sendVerificationEmail(new VerificationEmailRequested(1L, "raw-token")))
                .doesNotThrowAnyException();
    }

    @Test
    void swallowsPasswordResetDeliveryFailureWithoutRenderingTheRawToken() {
        UserAccount user = UserAccount.client("ana@example.com", "hash", Instant.parse("2026-07-29T00:00:00Z"));
        UserAccountRepository users = repositoryReturning(user);
        EmailSender failingSender = message -> {
            throw new IllegalStateException("smtp unavailable");
        };
        AccountEmailListener listener = new AccountEmailListener(users, properties(), failingSender, Runnable::run);
        PasswordResetEmailRequested event = new PasswordResetEmailRequested(1L, "raw-token-that-must-not-be-logged");

        assertThatCode(() -> listener.sendPasswordResetEmail(event)).doesNotThrowAnyException();
        org.assertj.core.api.Assertions.assertThat(event.toString()).doesNotContain("raw-token-that-must-not-be-logged");
    }

    @Test
    void afterCommitRecoveryHandoffReturnsWithoutWaitingForABlockingEmailSender() throws Exception {
        CountDownLatch senderStarted = new CountDownLatch(1);
        CountDownLatch releaseSender = new CountDownLatch(1);
        ThreadPoolExecutor executor = executor();
        try (AnnotationConfigApplicationContext context = listenerContext(message -> {
            senderStarted.countDown();
            try {
                assertThat(releaseSender.await(5, TimeUnit.SECONDS)).isTrue();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AssertionError("delivery worker was interrupted", exception);
            }
        }, executor)) {
            publishAfterCommit(context, new PasswordResetEmailRequested(1L, "raw-token"));

            assertThat(senderStarted.await(1, TimeUnit.SECONDS)).isTrue();
            // The transaction callback has already returned even though the delivery worker is blocked.
            assertThat(executor.getActiveCount()).isEqualTo(1);
        } finally {
            releaseSender.countDown();
            executor.shutdown();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void afterCommitVerificationHandoffAlsoUsesTheBoundedAsyncExecutor() throws Exception {
        CountDownLatch sent = new CountDownLatch(1);
        ThreadPoolExecutor executor = executor();
        try (AnnotationConfigApplicationContext context = listenerContext(message -> sent.countDown(), executor)) {
            publishAfterCommit(context, new VerificationEmailRequested(1L, "raw-token"));
            assertThat(sent.await(1, TimeUnit.SECONDS)).isTrue();
        } finally {
            executor.shutdown();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void queueSaturationIsContainedWithoutRunningDeliveryOnTheRequestThread() {
        UserAccount user = UserAccount.client("ana@example.com", "hash", Instant.parse("2026-07-29T00:00:00Z"));
        AccountEmailListener listener = new AccountEmailListener(repositoryReturning(user), properties(),
                message -> { throw new AssertionError("delivery must not run when rejected"); },
                task -> { throw new RejectedExecutionException("bounded queue full"); });

        assertThatCode(() -> listener.sendPasswordResetEmail(new PasswordResetEmailRequested(1L, "raw-token")))
                .doesNotThrowAnyException();
    }

    @Test
    void queueSaturationDurablyBacksOffTheExactOwnedAttempt() {
        Instant now = Instant.parse("2026-07-29T00:00:00Z");
        UserAccount user = UserAccount.client("ana@example.com", "hash", now);
        EmailDeliveryJob job = EmailDeliveryJob.create(
                user, EmailDeliveryType.PASSWORD_RESET, "9".repeat(64), now);
        setId(job, 91L);
        EmailDeliveryOutbox outbox = new EmailDeliveryOutbox(
                jobRepository(job), Clock.fixed(now, ZoneOffset.UTC));
        AccountEmailListener listener = new AccountEmailListener(
                repositoryReturning(user),
                properties(),
                message -> { throw new AssertionError("rejected task must not send"); },
                task -> { throw new RejectedExecutionException("bounded queue full"); },
                outbox);

        listener.sendPasswordResetEmail(new PasswordResetEmailRequested(
                1L, job.getId(), job.getDeliveryFence(), "raw-token"));

        assertThat(job.getState()).isEqualTo(EmailDeliveryJob.State.PENDING);
        assertThat(job.getAttempts()).isOne();
        assertThat(job.getNextAttemptAt()).isAfter(now);
    }

    private static AnnotationConfigApplicationContext listenerContext(EmailSender sender, ThreadPoolExecutor executor) {
        UserAccount user = UserAccount.client("ana@example.com", "hash", Instant.parse("2026-07-29T00:00:00Z"));
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.registerBean(EventListenerMethodProcessor.class);
        context.registerBean(TransactionalEventListenerFactory.class);
        context.registerBean(AccountEmailListener.class,
                () -> new AccountEmailListener(repositoryReturning(user), properties(), sender, executor));
        context.refresh();
        return context;
    }

    private static void publishAfterCommit(AnnotationConfigApplicationContext context, Object event) {
        new TransactionTemplate(new TestTransactionManager()).executeWithoutResult(status -> context.publishEvent(event));
    }

    private static ThreadPoolExecutor executor() {
        return new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(1));
    }

    private static final class TestTransactionManager extends AbstractPlatformTransactionManager {
        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
        }
    }

    private static UserAccountRepository repositoryReturning(UserAccount user) {
        return (UserAccountRepository) Proxy.newProxyInstance(
                UserAccountRepository.class.getClassLoader(),
                new Class<?>[]{UserAccountRepository.class},
                (proxy, method, arguments) -> method.getName().equals("findById") ? Optional.of(user) : null);
    }

    private static EmailDeliveryJobRepository jobRepository(EmailDeliveryJob job) {
        return (EmailDeliveryJobRepository) Proxy.newProxyInstance(
                EmailDeliveryJobRepository.class.getClassLoader(),
                new Class<?>[]{EmailDeliveryJobRepository.class},
                (proxy, method, arguments) -> method.getName().equals("findByIdForUpdate")
                        ? Optional.of(job)
                        : null);
    }

    private static void setId(EmailDeliveryJob job, Long id) {
        try {
            Field field = EmailDeliveryJob.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(job, id);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }

    private static AmidogProperties properties() {
        return new AmidogProperties(
                ZoneId.of("America/Santiago"), URI.create("http://localhost:5173"),
                URI.create("http://localhost:5173"),
                new AmidogProperties.Booking(false, 30, 2, 90),
                new AmidogProperties.Admin("", "", "", ""),
                new AmidogProperties.Contact("", ""),
                new AmidogProperties.Email("log"));
    }
}
