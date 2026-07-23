package com.amidog.app.auth;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface EmailDeliveryJobRepository extends JpaRepository<EmailDeliveryJob, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select job from EmailDeliveryJob job where job.id = :id")
    Optional<EmailDeliveryJob> findByIdForUpdate(@Param("id") Long id);

    @Query(value = """
            select id
            from email_delivery_jobs
            where (state = 'PENDING' and next_attempt_at <= :now)
               or (state = 'PROCESSING' and lease_until <= :now)
            order by next_attempt_at, id
            limit 20
            for update skip locked
            """, nativeQuery = true)
    List<Long> findDueIdsForUpdateSkipLocked(@Param("now") Instant now);
}
