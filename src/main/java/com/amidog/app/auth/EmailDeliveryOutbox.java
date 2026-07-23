package com.amidog.app.auth;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Service
public class EmailDeliveryOutbox {

    private final EmailDeliveryJobRepository jobs;
    private final Clock clock;

    public EmailDeliveryOutbox(EmailDeliveryJobRepository jobs, Clock clock) {
        this.jobs = jobs;
        this.clock = clock;
    }

    @Transactional
    public DeliveryAttempt enqueue(UserAccount user, EmailDeliveryType type, String tokenHash) {
        EmailDeliveryJob job = EmailDeliveryJob.create(user, type,
                EmailDeliveryJob.requireValidTokenHash(tokenHash), clock.instant());
        job = jobs.save(job);
        return attempt(job);
    }

    @Transactional
    public boolean delivered(Long id, String fence) {
        return jobs.findByIdForUpdate(id)
                .map(job -> job.complete(fence, clock.instant()))
                .orElse(false);
    }

    @Transactional
    public boolean failed(Long id, String fence) {
        return jobs.findByIdForUpdate(id)
                .map(job -> job.retry(fence, clock.instant()))
                .orElse(false);
    }

    @Transactional
    public List<DeliveryAttempt> claimDue() {
        Instant now = clock.instant();
        return jobs.findDueIdsForUpdateSkipLocked(now).stream()
                .map(id -> jobs.findByIdForUpdate(id).orElse(null))
                .filter(job -> job != null && job.claim(now))
                .map(EmailDeliveryOutbox::attempt)
                .toList();
    }

    private static DeliveryAttempt attempt(EmailDeliveryJob job) {
        return new DeliveryAttempt(job.getId(), job.getDeliveryFence(), job.getUser().getId(),
                job.getDeliveryType(), job.getTokenHash());
    }
}

record DeliveryAttempt(Long jobId, String fence, Long userId, EmailDeliveryType deliveryType, String tokenHash) {
}
