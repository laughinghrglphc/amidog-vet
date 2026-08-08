package com.amidog.app.auth;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmailDeliveryOutboxTests {

    private static final Instant CREATED_AT = Instant.parse("2026-07-29T00:00:00Z");
    private static final Instant CALLBACK_AT = CREATED_AT.plusSeconds(62);

    @Test
    void staleDeliveredCallbackCannotCompleteANewerRepositoryFence() {
        Fixture fixture = new Fixture();
        String staleFence = fixture.originalFence;

        assertThat(fixture.outbox.delivered(1L, staleFence)).isFalse();

        assertThat(fixture.job.getState()).isEqualTo(EmailDeliveryJob.State.PROCESSING);
        assertThat(fixture.job.getDeliveryFence()).isEqualTo(fixture.currentFence);
        assertThat(fixture.job.getCompletedAt()).isNull();
        assertThat(fixture.repository.lockedIds).containsExactly(1L);

        assertThat(fixture.outbox.delivered(1L, fixture.currentFence)).isTrue();
        assertThat(fixture.job.getState()).isEqualTo(EmailDeliveryJob.State.COMPLETED);
        assertThat(fixture.job.getCompletedAt()).isEqualTo(CALLBACK_AT);
    }

    @Test
    void staleFailedCallbackCannotReopenOrBackoffANewerRepositoryFence() {
        Fixture fixture = new Fixture();
        String staleFence = fixture.originalFence;

        assertThat(fixture.outbox.failed(1L, staleFence)).isFalse();

        assertThat(fixture.job.getState()).isEqualTo(EmailDeliveryJob.State.PROCESSING);
        assertThat(fixture.job.getDeliveryFence()).isEqualTo(fixture.currentFence);
        assertThat(fixture.job.getAttempts()).isZero();
        assertThat(fixture.repository.lockedIds).containsExactly(1L);

        assertThat(fixture.outbox.failed(1L, fixture.currentFence)).isTrue();
        assertThat(fixture.job.getState()).isEqualTo(EmailDeliveryJob.State.PENDING);
        assertThat(fixture.job.getAttempts()).isOne();
        assertThat(fixture.job.getNextAttemptAt()).isAfter(CALLBACK_AT);
    }

    @Test
    void enqueueRejectsInvalidHashesBeforeRepositoryMutation() {
        AtomicInteger saves = new AtomicInteger();
        EmailDeliveryJobRepository repository = (EmailDeliveryJobRepository) Proxy.newProxyInstance(
                EmailDeliveryJobRepository.class.getClassLoader(),
                new Class<?>[]{EmailDeliveryJobRepository.class},
                (proxy, method, arguments) -> {
                    if (method.getName().equals("save")) {
                        saves.incrementAndGet();
                    }
                    if (method.getName().equals("toString")) {
                        return "rejecting-repository";
                    }
                    return null;
                });
        EmailDeliveryOutbox outbox = new EmailDeliveryOutbox(
                repository, Clock.fixed(CREATED_AT, ZoneOffset.UTC));
        UserAccount user = UserAccount.client("client@example.com", "hash", CREATED_AT);

        for (String invalid : new String[]{null, "a".repeat(63), "a".repeat(65),
                "A".repeat(64), "z".repeat(64)}) {
            assertThatThrownBy(() -> outbox.enqueue(
                    user, EmailDeliveryType.VERIFICATION, invalid))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        assertThat(saves).hasValue(0);
    }

    private static final class Fixture {
        private final EmailDeliveryJob job;
        private final String originalFence;
        private final String currentFence;
        private final InMemoryJobRepository repository;
        private final EmailDeliveryOutbox outbox;

        private Fixture() {
            job = EmailDeliveryJob.create(
                    UserAccount.client("client@example.com", "hash", CREATED_AT),
                    EmailDeliveryType.PASSWORD_RESET,
                    "8".repeat(64),
                    CREATED_AT);
            setId(job, 1L);
            originalFence = job.getDeliveryFence();
            assertThat(job.claim(CREATED_AT.plusSeconds(61))).isTrue();
            currentFence = job.getDeliveryFence();
            repository = new InMemoryJobRepository(Map.of(1L, job));
            outbox = new EmailDeliveryOutbox(
                    repository.proxy(), Clock.fixed(CALLBACK_AT, ZoneOffset.UTC));
        }
    }

    private static final class InMemoryJobRepository {
        private final Map<Long, EmailDeliveryJob> jobs = new LinkedHashMap<>();
        private final List<Long> lockedIds = new java.util.ArrayList<>();

        private InMemoryJobRepository(Map<Long, EmailDeliveryJob> initial) {
            jobs.putAll(initial);
        }

        private EmailDeliveryJobRepository proxy() {
            return (EmailDeliveryJobRepository) Proxy.newProxyInstance(
                    EmailDeliveryJobRepository.class.getClassLoader(),
                    new Class<?>[]{EmailDeliveryJobRepository.class},
                    (proxy, method, arguments) -> {
                        if (method.getName().equals("findByIdForUpdate")) {
                            Long id = (Long) arguments[0];
                            lockedIds.add(id);
                            return Optional.ofNullable(jobs.get(id));
                        }
                        if (method.getName().equals("findDueIdsForUpdateSkipLocked")) {
                            return List.of();
                        }
                        if (method.getName().equals("toString")) {
                            return "in-memory-job-repository";
                        }
                        return null;
                    });
        }
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
}
