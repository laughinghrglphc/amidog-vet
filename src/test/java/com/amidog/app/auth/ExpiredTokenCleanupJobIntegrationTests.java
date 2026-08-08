package com.amidog.app.auth;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.data.jpa.repository.Query;
import org.springframework.scheduling.annotation.Scheduled;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Deterministic scheduled-job and repository-metadata contract tests. They do
 * not claim to execute PostgreSQL; database execution remains Docker-gated.
 */
class ExpiredTokenCleanupJobIntegrationTests {

    private static final Instant NOW =
            Instant.parse("2026-07-30T12:00:00Z");
    private static final Instant SEVEN_DAYS_AGO =
            Instant.parse("2026-07-23T12:00:00Z");

    @Test
    void usesOneClockInstantAndTheExactSevenDayConsumedRetentionBoundary() {
        List<DeletionCall> verificationCalls = new ArrayList<>();
        List<DeletionCall> resetCalls = new ArrayList<>();
        CountingClock clock = new CountingClock(NOW);
        ExpiredTokenCleanupJob job = new ExpiredTokenCleanupJob(
                verificationRepository(verificationCalls),
                resetRepository(resetCalls),
                clock);

        job.removeExpiredAndConsumedTokens();

        assertThat(clock.calls()).isEqualTo(1);
        assertThat(verificationCalls).containsExactly(
                new DeletionCall(SEVEN_DAYS_AGO, NOW));
        assertThat(resetCalls).containsExactly(
                new DeletionCall(SEVEN_DAYS_AGO, NOW));
    }

    @ParameterizedTest(name = "{1}")
    @MethodSource("cleanupRepositories")
    void repositoryContractsDeleteExpiredOrSevenDayConsumedTokensOnly(
            Class<?> repositoryType,
            String entityName) throws Exception {
        assertCleanupContract(repositoryType, entityName);
    }

    @Test
    void schedulerUsesTheDailyCronAndDelegatesToTheCallableCleanupMethod()
            throws Exception {
        AtomicInteger calls = new AtomicInteger();
        ExpiredTokenCleanupJob job = new ExpiredTokenCleanupJob(
                verificationRepository(new ArrayList<>()),
                resetRepository(new ArrayList<>()),
                Clock.fixed(NOW, ZoneOffset.UTC)) {
            @Override
            public void removeExpiredAndConsumedTokens() {
                calls.incrementAndGet();
            }
        };

        job.runCleanup();

        Scheduled schedule = ExpiredTokenCleanupJob.class
                .getMethod("runCleanup")
                .getAnnotation(Scheduled.class);
        assertThat(calls).hasValue(1);
        assertThat(schedule.cron()).isEqualTo(
                "${amidog.tokens.cleanup-cron:0 30 3 * * *}");
        assertThat(schedule.zone()).isEqualTo(
                "${amidog.clinic-zone:America/Santiago}");
    }

    private static void assertCleanupContract(
            Class<?> repositoryType,
            String entityName) throws Exception {
        Method method = repositoryType.getMethod(
                "deleteExpiredOrConsumedBefore",
                Instant.class,
                Instant.class);
        Query query = method.getAnnotation(Query.class);
        String normalized = query.value()
                .replaceAll("\\s+", " ")
                .trim();

        assertThat(normalized).isEqualTo(
                "delete from " + entityName + " token "
                        + "where ( token.consumedAt is null "
                        + "and token.expiresAt <= :now ) "
                        + "or ( token.consumedAt is not null "
                        + "and token.consumedAt <= :consumedCutoff )");
    }

    private static Stream<Arguments> cleanupRepositories() {
        return Stream.of(
                Arguments.of(
                        EmailVerificationTokenRepository.class,
                        "EmailVerificationToken"),
                Arguments.of(
                        PasswordResetTokenRepository.class,
                        "PasswordResetToken"));
    }

    private static EmailVerificationTokenRepository
    verificationRepository(List<DeletionCall> calls) {
        return proxy(
                EmailVerificationTokenRepository.class,
                (method, arguments) -> recordDeletion(
                        method.getName(), arguments, calls));
    }

    private static PasswordResetTokenRepository
    resetRepository(List<DeletionCall> calls) {
        return proxy(
                PasswordResetTokenRepository.class,
                (method, arguments) -> recordDeletion(
                        method.getName(), arguments, calls));
    }

    private static Object recordDeletion(
            String methodName,
            Object[] arguments,
            List<DeletionCall> calls) {
        if (!methodName.equals("deleteExpiredOrConsumedBefore")) {
            throw new UnsupportedOperationException(methodName);
        }
        calls.add(new DeletionCall(
                (Instant) arguments[0],
                (Instant) arguments[1]));
        return 0;
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(
            Class<T> type,
            Invocation invocation) {
        return (T) Proxy.newProxyInstance(
                type.getClassLoader(),
                new Class<?>[]{type},
                (proxy, method, arguments) -> {
                    if (method.getDeclaringClass() == Object.class) {
                        return switch (method.getName()) {
                            case "toString" ->
                                    type.getSimpleName() + "Proxy";
                            case "hashCode" ->
                                    System.identityHashCode(proxy);
                            case "equals" -> proxy == arguments[0];
                            default -> null;
                        };
                    }
                    return invocation.invoke(method, arguments);
                });
    }

    private record DeletionCall(
            Instant consumedCutoff,
            Instant now) {
    }

    private static final class CountingClock extends Clock {

        private final Instant instant;
        private int calls;

        private CountingClock(Instant instant) {
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
            Instant result = instant.plus(
                    Duration.ofHours(calls));
            calls++;
            return result;
        }

        int calls() {
            return calls;
        }
    }

    @FunctionalInterface
    private interface Invocation {
        Object invoke(Method method, Object[] arguments)
                throws Throwable;
    }
}
