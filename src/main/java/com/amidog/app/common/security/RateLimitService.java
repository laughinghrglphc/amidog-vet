package com.amidog.app.common.security;

import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class RateLimitService {

    private static final long CLEANUP_INTERVAL = 1_000;

    private final Clock clock;
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
    private final AtomicLong acquisitions = new AtomicLong();

    public RateLimitService(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public boolean tryAcquire(String key, int limit, Duration duration) {
        validate(key, limit, duration);
        Instant now = clock.instant();
        requireRepresentableWindow(now, duration);
        AtomicBoolean admitted = new AtomicBoolean();

        windows.compute(key, (ignored, current) -> {
            if (current == null
                    || !current.duration().equals(duration)
                    || hasExpired(current, now)) {
                admitted.set(true);
                return new Window(now, 1, duration);
            }
            if (current.count() >= limit) {
                admitted.set(false);
                return current;
            }
            admitted.set(true);
            return new Window(current.startedAt(), current.count() + 1, current.duration());
        });

        if (acquisitions.incrementAndGet() % CLEANUP_INTERVAL == 0) {
            removeExpiredWindows(now);
        }
        return admitted.get();
    }

    private void removeExpiredWindows(Instant now) {
        windows.forEach((key, window) -> {
            if (hasExpired(window, now)) {
                windows.remove(key, window);
            }
        });
    }

    private boolean hasExpired(Window window, Instant now) {
        return !now.isBefore(window.startedAt().plus(window.duration()));
    }

    private void validate(String key, int limit, Duration duration) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Rate limit key must not be blank");
        }
        if (limit <= 0) {
            throw new IllegalArgumentException("Rate limit must be positive");
        }
        if (duration == null || duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException("Rate limit duration must be positive");
        }
    }

    private void requireRepresentableWindow(Instant now, Duration duration) {
        try {
            now.plus(duration);
        } catch (DateTimeException | ArithmeticException exception) {
            throw new IllegalArgumentException("Rate limit duration is too large");
        }
    }

    private record Window(Instant startedAt, int count, Duration duration) {
    }
}
