package com.amidog.app.auth;

import com.amidog.app.client.ClientRepository;
import com.amidog.app.email.EmailMessage;
import com.amidog.app.email.EmailSender;
import com.amidog.app.support.PostgresIntegrationTest;
import com.amidog.app.support.RateLimitTestSupport;
import com.amidog.app.common.security.RateLimitService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@Import({
        RegistrationIntegrationTests.EmailCaptureConfiguration.class,
        RegistrationIntegrationTests.LockCoordinationConfiguration.class
})
class RegistrationIntegrationTests extends PostgresIntegrationTest {

    private static final String ACCEPTED_MESSAGE = "Revisa tu correo para verificar tu cuenta.";
    private static final Pattern TOKEN_PATTERN = Pattern.compile("token=([^\\s]+)");

    @Autowired MockMvc mvc;
    @Autowired UserAccountRepository users;
    @Autowired ClientRepository clients;
    @Autowired EmailVerificationTokenRepository verificationTokens;
    @Autowired CapturingEmailSender emailSender;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired ApplicationEventPublisher events;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired LockOrderHooks lockOrderHooks;
    @Autowired JdbcTemplate jdbc;
    @Autowired RateLimitService rateLimits;

    @BeforeEach
    void cleanDatabase() {
        RateLimitTestSupport.reset(rateLimits);
        verificationTokens.deleteAll();
        clients.deleteAll();
        users.deleteAll();
        emailSender.clear();
    }

    @Test
    void registersAnUnverifiedClientWithBcryptPasswordAndOnlyATokenHash() throws Exception {
        register(" Ana@Example.com ");

        UserAccount user = users.findByEmailNormalized("ana@example.com").orElseThrow();
        assertThat(user.isVerified()).isFalse();
        assertThat(user.isEnabled()).isFalse();
        assertThat(user.getPasswordHash()).startsWith("{bcrypt}$2").doesNotContain("Correct-Horse-9!");
        assertThat(passwordEncoder.matches("Correct-Horse-9!", user.getPasswordHash())).isTrue();
        assertThat(clients.findByUserId(user.getId())).isPresent();
        assertThat(verificationTokens.findAll()).singleElement()
                .extracting(EmailVerificationToken::getTokenHash)
                .asString().hasSize(64);
    }

    @Test
    void normalizedDuplicateRegistrationHasTheSameAcceptedResponseAndDoesNotIssueAnotherToken() throws Exception {
        register("ana@example.com");
        String originalHash = verificationTokens.findAll().getFirst().getTokenHash();
        assertThat(emailSender.awaitMessages(1)).hasSize(1);
        emailSender.clear();

        register(" ANA@EXAMPLE.COM ");

        assertThat(users.findAll()).hasSize(1);
        assertThat(verificationTokens.findAll()).singleElement()
                .extracting(EmailVerificationToken::getTokenHash).isEqualTo(originalHash);
        assertThat(emailSender.messages()).isEmpty();
    }

    @Test
    void sendsTheRawVerificationLinkOnlyAfterTheRegistrationTransactionCommits() throws Exception {
        register("ana@example.com");

        EmailMessage email = emailSender.awaitMessages(1).getFirst();
        String rawToken = rawToken(email);
        assertThat(email.to()).isEqualTo("ana@example.com");
        assertThat(email.text()).contains("http://localhost:5173/verificar-correo?token=");
        assertThat(verificationTokens.findByTokenHash(rawToken)).isEmpty();
        assertThat(verificationTokens.findByTokenHash(new SecureTokenService().hash(rawToken))).isPresent();
    }

    @Test
    void verifiesAValidTokenExactlyOnce() throws Exception {
        register("ana@example.com");
        String rawToken = rawToken(emailSender.awaitMessages(1).getFirst());

        verify(rawToken).andExpect(status().isNoContent());
        verify(rawToken).andExpect(status().isBadRequest());

        assertThat(users.findByEmailNormalized("ana@example.com").orElseThrow().isVerified()).isTrue();
    }

    @Test
    void rejectsUnknownAndExpiredVerificationTokensWithTheSameBadRequest() throws Exception {
        register("ana@example.com");
        String expiredRawToken = rawToken(emailSender.awaitMessages(1).getFirst());
        EmailVerificationToken original = verificationTokens.findAll().getFirst();
        verificationTokens.deleteAll();
        verificationTokens.saveAndFlush(EmailVerificationToken.create(
                users.findByEmailNormalized("ana@example.com").orElseThrow(),
                new SecureTokenService().hash(expiredRawToken),
                Instant.now().minusSeconds(1), Instant.now().minusSeconds(86_400)));

        verify("not-a-real-token").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("El enlace de verificación no es válido o expiró."));
        verify(expiredRawToken).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("El enlace de verificación no es válido o expiró."));
        assertThat(original.getTokenHash()).hasSize(64);
    }

    @Test
    void resendDoesNotEnumerateAndInvalidatesThePriorToken() throws Exception {
        register("ana@example.com");
        String originalRawToken = rawToken(emailSender.awaitMessages(1).getFirst());
        emailSender.clear();

        resend("ana@example.com");
        String resentRawToken = rawToken(emailSender.awaitMessages(1).getFirst());
        verify(originalRawToken).andExpect(status().isBadRequest());
        verify(resentRawToken).andExpect(status().isNoContent());

        emailSender.clear();
        resend("unknown@example.com");
        assertThat(emailSender.messages()).isEmpty();

        resend("ana@example.com");
        assertThat(emailSender.messages()).isEmpty();
    }

    @Test
    void validatesRequestsAndKeepsCsrfProtectionOnPublicMutations() throws Exception {
        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson("ana@example.com")))
                .andExpect(status().isForbidden());

        mvc.perform(post("/api/v1/auth/register").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson("not-an-email")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void concurrentVerifyRequestsHaveOneSuccessAndOneStableFailure() throws Exception {
        register("ana@example.com");
        String rawToken = rawToken(emailSender.awaitMessages(1).getFirst());

        List<MvcResult> results = concurrently(() -> verifyResult(rawToken), () -> verifyResult(rawToken));

        assertThat(results).extracting(result -> result.getResponse().getStatus())
                .containsExactlyInAnyOrder(204, 400);
        assertThat(users.findByEmailNormalized("ana@example.com").orElseThrow().isVerified()).isTrue();
        assertThat(verificationTokens.findAll()).singleElement()
                .extracting(EmailVerificationToken::getConsumedAt).isNotNull();
    }

    @Test
    void concurrentVerifyAndResendCompleteWithoutDeadlockOrServerError() throws Exception {
        register("ana@example.com");
        String rawToken = rawToken(emailSender.awaitMessages(1).getFirst());
        emailSender.clear();

        List<MvcResult> results = concurrently(() -> verifyResult(rawToken), () -> resendResult("ana@example.com"));

        assertThat(results).extracting(result -> result.getResponse().getStatus())
                .doesNotContain(500)
                .contains(202);
        int verifyStatus = results.stream()
                .mapToInt(result -> result.getResponse().getStatus())
                .filter(status -> status != 202)
                .findFirst().orElseThrow();
        assertThat(verifyStatus).isIn(204, 400);

        UserAccount account = users.findByEmailNormalized("ana@example.com").orElseThrow();
        long activeTokenCount = verificationTokens.findAll().stream()
                .filter(token -> token.getConsumedAt() == null)
                .count();
        if (account.isVerified()) {
            assertThat(verifyStatus).isEqualTo(204);
            assertThat(activeTokenCount).isZero();
            assertThat(emailSender.messages()).isEmpty();
        } else {
            assertThat(verifyStatus).isEqualTo(400);
            assertThat(activeTokenCount).isEqualTo(1);
            assertThat(emailSender.awaitMessages(1)).hasSize(1);
        }
    }

    @Test
    void verifyHoldingTheActualUserLockAllowsResendContentionWithoutTokenToUserDeadlock() throws Exception {
        register("ana@example.com");
        String rawToken = rawToken(emailSender.awaitMessages(1).getFirst());
        emailSender.clear();
        lockOrderHooks.arm();
        ExecutorService executor = Executors.newFixedThreadPool(2, runnable -> {
            Thread thread = new Thread(runnable);
            thread.setName(lockOrderHooks.threadNameForNextWorker());
            return thread;
        });
        try {
            Future<MvcResult> verifyResult = executor.submit(() -> verifyResult(rawToken));
            assertThat(lockOrderHooks.awaitVerifyUserLock()).isTrue();

            Future<MvcResult> resendResult = executor.submit(() -> resendResult("ana@example.com"));
            assertThat(lockOrderHooks.awaitResendQueryStarted()).isTrue();
            assertThat(awaitMarkedResendLockWait()).isTrue();
            lockOrderHooks.releaseVerify();

            assertThat(verifyResult.get(10, TimeUnit.SECONDS).getResponse().getStatus()).isEqualTo(204);
            assertThat(resendResult.get(10, TimeUnit.SECONDS).getResponse().getStatus()).isEqualTo(202);
            assertThat(users.findByEmailNormalized("ana@example.com").orElseThrow().isVerified()).isTrue();
            assertThat(emailSender.messages()).isEmpty();
            assertThat(markedResendBackendCount()).isZero();
        } finally {
            lockOrderHooks.disarm();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void concurrentResendsStayAcceptedAndLeaveOneActiveToken() throws Exception {
        register("ana@example.com");
        assertThat(emailSender.awaitMessages(1)).hasSize(1);
        emailSender.clear();

        List<MvcResult> results = concurrently(
                () -> resendResult("ana@example.com"), () -> resendResult("ana@example.com"));

        assertThat(results).extracting(result -> result.getResponse().getStatus()).containsOnly(202);
        assertThat(verificationTokens.findAll().stream().filter(token -> token.getConsumedAt() == null).toList()).hasSize(1);
        assertThat(emailSender.awaitMessages(2)).hasSize(2);
    }

    @Test
    void resendForKnownAdministratorHasTheSameAcceptedResponseAndDoesNotSendEmail() throws Exception {
        users.saveAndFlush(UserAccount.admin("admin@example.com", "hash", Instant.parse("2026-07-29T00:00:00Z")));

        resend("admin@example.com");

        assertThat(verificationTokens.findAll()).isEmpty();
        assertThat(emailSender.messages()).isEmpty();
    }

    @Test
    void verificationEmailIsNotDeliveredBeforeCommitOrAfterRollback() {
        UserAccount user = users.saveAndFlush(UserAccount.client(
                "rollback@example.com", "hash", Instant.parse("2026-07-29T00:00:00Z")));
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        transaction.executeWithoutResult(status -> {
            events.publishEvent(new VerificationEmailRequested(user.getId(), "raw-token-for-rollback"));
            assertThat(emailSender.messages()).isEmpty();
            status.setRollbackOnly();
        });

        assertThat(emailSender.messages()).isEmpty();
    }

    private void register(String email) throws Exception {
        mvc.perform(post("/api/v1/auth/register").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson(email)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.message").value(ACCEPTED_MESSAGE));
    }

    private org.springframework.test.web.servlet.ResultActions verify(String rawToken) throws Exception {
        return mvc.perform(post("/api/v1/auth/verify-email").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + rawToken + "\"}"));
    }

    private MvcResult verifyResult(String rawToken) throws Exception {
        return verify(rawToken).andReturn();
    }

    private void resend(String email) throws Exception {
        mvc.perform(post("/api/v1/auth/resend-verification").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.message").value(ACCEPTED_MESSAGE));
    }

    private MvcResult resendResult(String email) throws Exception {
        return mvc.perform(post("/api/v1/auth/resend-verification").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\"}"))
                .andReturn();
    }

    private List<MvcResult> concurrently(ConcurrentRequest first, ConcurrentRequest second) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<MvcResult> firstResult = executor.submit(() -> awaitAndRun(ready, start, first));
            Future<MvcResult> secondResult = executor.submit(() -> awaitAndRun(ready, start, second));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            return List.of(firstResult.get(10, TimeUnit.SECONDS), secondResult.get(10, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }
    }

    private MvcResult awaitAndRun(CountDownLatch ready, CountDownLatch start, ConcurrentRequest request) throws Exception {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Concurrent test did not start");
        }
        return request.run();
    }

    private boolean awaitMarkedResendLockWait() throws InterruptedException {
        CompletableFuture<Boolean> observed = new CompletableFuture<>();
        ScheduledExecutorService poller = Executors.newSingleThreadScheduledExecutor();
        poller.scheduleAtFixedRate(() -> {
            try {
                Boolean waiting = jdbc.queryForObject("""
                        select exists (
                            select 1
                            from pg_stat_activity
                            where application_name = 'amidog-verify-resend-lock-probe'
                              and state = 'active'
                              and wait_event_type = 'Lock'
                              and cardinality(pg_blocking_pids(pid)) > 0
                        )
                        """, Boolean.class);
                if (Boolean.TRUE.equals(waiting)) {
                    observed.complete(true);
                }
            } catch (RuntimeException exception) {
                observed.completeExceptionally(exception);
            }
        }, 0, 25, TimeUnit.MILLISECONDS);
        try {
            return observed.get(5, TimeUnit.SECONDS);
        } catch (TimeoutException exception) {
            return false;
        } catch (ExecutionException exception) {
            throw new AssertionError("PostgreSQL lock observation failed", exception.getCause());
        } finally {
            poller.shutdownNow();
            assertThat(poller.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    private int markedResendBackendCount() {
        Integer count = jdbc.queryForObject("""
                select count(*)
                from pg_stat_activity
                where application_name = 'amidog-verify-resend-lock-probe'
                """, Integer.class);
        return count == null ? 0 : count;
    }

    private String registerJson(String email) {
        return "{\"email\":\"" + email + "\",\"password\":\"Correct-Horse-9!\","
                + "\"name\":\"Ana Pérez\",\"phone\":\"+56 9 1234 5678\"}";
    }

    private String rawToken(EmailMessage email) {
        Matcher matcher = TOKEN_PATTERN.matcher(email.text());
        assertThat(matcher.find()).isTrue();
        return URLDecoder.decode(matcher.group(1), StandardCharsets.UTF_8);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class EmailCaptureConfiguration {
        @Bean
        @Primary
        CapturingEmailSender capturingEmailSender() {
            return new CapturingEmailSender();
        }
    }

    static final class CapturingEmailSender implements EmailSender {
        private final List<EmailMessage> messages = new CopyOnWriteArrayList<>();

        @Override
        public void send(EmailMessage message) {
            synchronized (messages) { messages.add(message); messages.notifyAll(); }
        }

        List<EmailMessage> messages() {
            return List.copyOf(messages);
        }

        List<EmailMessage> awaitMessages(int count) {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            synchronized (messages) {
                while (messages.size() < count && System.nanoTime() < deadline) {
                    try { TimeUnit.NANOSECONDS.timedWait(messages, deadline - System.nanoTime()); }
                    catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new AssertionError(exception); }
                }
                return List.copyOf(messages);
            }
        }

        void clear() {
            synchronized (messages) { messages.clear(); }
        }
    }

    @FunctionalInterface
    private interface ConcurrentRequest {
        MvcResult run() throws Exception;
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableAspectJAutoProxy
    static class LockCoordinationConfiguration {
        @Bean
        LockOrderHooks lockOrderHooks() {
            return new LockOrderHooks();
        }

        @Bean
        RepositoryLockProbe repositoryLockProbe(LockOrderHooks hooks, JdbcTemplate jdbc) {
            return new RepositoryLockProbe(hooks, jdbc);
        }
    }

    @Aspect
    static class RepositoryLockProbe {
        private final LockOrderHooks hooks;
        private final JdbcTemplate jdbc;

        RepositoryLockProbe(LockOrderHooks hooks, JdbcTemplate jdbc) {
            this.hooks = hooks;
            this.jdbc = jdbc;
        }

        @Around("execution(* com.amidog.app.auth.UserAccountRepository.findByIdForUpdate(..))")
        Object holdVerifyAfterItAcquiresTheUserLock(ProceedingJoinPoint joinPoint) throws Throwable {
            Object result = joinPoint.proceed();
            if (hooks.isVerifyThread()) {
                hooks.signalVerifyUserLock();
                hooks.awaitVerifyRelease();
            }
            return result;
        }

        @Around("execution(* com.amidog.app.auth.UserAccountRepository.findByEmailNormalizedForUpdate(..))")
        Object markAndAttemptTheSameUserLock(ProceedingJoinPoint joinPoint) throws Throwable {
            if (hooks.isResendThread()) {
                jdbc.execute("set local application_name = 'amidog-verify-resend-lock-probe'");
                hooks.signalResendQueryStarted();
            }
            return joinPoint.proceed();
        }
    }

    static final class LockOrderHooks {
        private volatile boolean armed;
        private volatile CountDownLatch verifyUserLock = new CountDownLatch(1);
        private volatile CountDownLatch resendQueryStarted = new CountDownLatch(1);
        private volatile CountDownLatch releaseVerify = new CountDownLatch(1);
        private int workerNumber;

        synchronized String threadNameForNextWorker() {
            return workerNumber++ == 0 ? "verify-lock-order" : "resend-lock-order";
        }

        void arm() {
            armed = true;
            workerNumber = 0;
            verifyUserLock = new CountDownLatch(1);
            resendQueryStarted = new CountDownLatch(1);
            releaseVerify = new CountDownLatch(1);
        }

        void disarm() {
            armed = false;
            releaseVerify.countDown();
        }

        boolean isVerifyThread() {
            return armed && Thread.currentThread().getName().equals("verify-lock-order");
        }

        boolean isResendThread() {
            return armed && Thread.currentThread().getName().equals("resend-lock-order");
        }

        void signalVerifyUserLock() {
            verifyUserLock.countDown();
        }

        void signalResendQueryStarted() {
            resendQueryStarted.countDown();
        }

        boolean awaitVerifyUserLock() throws InterruptedException {
            return verifyUserLock.await(5, TimeUnit.SECONDS);
        }

        boolean awaitResendQueryStarted() throws InterruptedException {
            return resendQueryStarted.await(5, TimeUnit.SECONDS);
        }

        void awaitVerifyRelease() throws InterruptedException {
            if (!releaseVerify.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Verify lock coordination timed out");
            }
        }

        void releaseVerify() {
            releaseVerify.countDown();
        }
    }
}
