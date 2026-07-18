package com.amidog.app.common.security;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RateLimitServiceTests {

    private final MutableClock clock = new MutableClock(
            Instant.parse("2026-07-29T12:00:00Z"), ZoneOffset.UTC);

    @Test
    void admitsUpToTheLimitAndRejectsTheNextAttempt() {
        RateLimitService limits = new RateLimitService(clock);

        IntStream.range(0, 5).forEach(index ->
                assertThat(limits.tryAcquire("login:127.0.0.1", 5, Duration.ofMinutes(1))).isTrue());

        assertThat(limits.tryAcquire("login:127.0.0.1", 5, Duration.ofMinutes(1))).isFalse();
    }

    @Test
    void resetsAtTheExactWindowBoundary() {
        RateLimitService limits = new RateLimitService(clock);
        Duration window = Duration.ofMinutes(15);

        IntStream.range(0, 10).forEach(index ->
                assertThat(limits.tryAcquire("login:127.0.0.1", 10, window)).isTrue());
        assertThat(limits.tryAcquire("login:127.0.0.1", 10, window)).isFalse();

        clock.advance(window.minusNanos(1));
        assertThat(limits.tryAcquire("login:127.0.0.1", 10, window)).isFalse();

        clock.advance(Duration.ofNanos(1));
        assertThat(limits.tryAcquire("login:127.0.0.1", 10, window)).isTrue();
    }

    @Test
    void tracksKeysAndPoliciesIndependently() {
        RateLimitService limits = new RateLimitService(clock);

        assertThat(limits.tryAcquire("register:127.0.0.1", 1, Duration.ofHours(1))).isTrue();
        assertThat(limits.tryAcquire("register:127.0.0.1", 1, Duration.ofHours(1))).isFalse();
        assertThat(limits.tryAcquire("register:127.0.0.2", 1, Duration.ofHours(1))).isTrue();
        assertThat(limits.tryAcquire("login:127.0.0.1", 1, Duration.ofMinutes(15))).isTrue();
    }

    @Test
    void neverOverAdmitsConcurrentAttemptsForOneKey() throws Exception {
        RateLimitService limits = new RateLimitService(clock);
        int contenders = 64;
        int allowed = 10;
        CountDownLatch ready = new CountDownLatch(contenders);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger admitted = new AtomicInteger();

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var tasks = IntStream.range(0, contenders)
                    .mapToObj(index -> executor.submit(() -> {
                        ready.countDown();
                        start.await();
                        if (limits.tryAcquire("login:127.0.0.1", allowed, Duration.ofMinutes(15))) {
                            admitted.incrementAndGet();
                        }
                        return null;
                    }))
                    .toList();

            ready.await();
            start.countDown();
            for (var task : tasks) {
                task.get();
            }
        }

        assertThat(admitted).hasValue(allowed);
    }

    @Test
    void rejectsInvalidArguments() {
        RateLimitService limits = new RateLimitService(clock);

        assertThatThrownBy(() -> limits.tryAcquire(null, 1, Duration.ofMinutes(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> limits.tryAcquire(" ", 1, Duration.ofMinutes(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> limits.tryAcquire("key", 0, Duration.ofMinutes(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> limits.tryAcquire("key", 1, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> limits.tryAcquire("key", 1, Duration.ofMinutes(-1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void removesExpiredEntriesOnEveryThousandthAcquisition() {
        RateLimitService limits = new RateLimitService(clock);
        Duration duration = Duration.ofMinutes(1);

        IntStream.range(0, 999).forEach(index ->
                limits.tryAcquire("forgot:user" + index + "@example.cl", 1, duration));
        clock.advance(duration);
        limits.tryAcquire("forgot:fresh@example.cl", 1, duration);

        Map<?, ?> windows = (Map<?, ?>) ReflectionTestUtils.getField(limits, "windows");
        assertThat(windows).hasSize(1);
    }
}
