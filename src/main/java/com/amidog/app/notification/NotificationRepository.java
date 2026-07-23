package com.amidog.app.notification;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface NotificationRepository
        extends JpaRepository<Notification, Long> {

    String DEDUPLICATION_CONSTRAINT =
            "notifications_deduplication_key_key";

    @Modifying(flushAutomatically = true)
    @Transactional
    @Query(value = """
            insert into notifications(
                recipient_user_id, type, title, body, reservation_id,
                deduplication_key, created_at)
            values (
                :recipientUserId, :type, :title, :body, :reservationId,
                :deduplicationKey, :createdAt)
            on conflict on constraint notifications_deduplication_key_key
            do nothing
            """, nativeQuery = true)
    int insertIgnoringDeduplicationConflict(
            @Param("recipientUserId") Long recipientUserId,
            @Param("type") String type,
            @Param("title") String title,
            @Param("body") String body,
            @Param("reservationId") Long reservationId,
            @Param("deduplicationKey") String deduplicationKey,
            @Param("createdAt") Instant createdAt);

    Optional<Notification> findByDeduplicationKey(String deduplicationKey);

    List<Notification>
    findAllByRecipientIdOrderByCreatedAtDescIdDesc(
            Long recipientUserId, Pageable pageable);

    Optional<Notification> findByIdAndRecipientId(
            Long id, Long recipientUserId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update Notification notification
            set notification.readAt = :readAt
            where notification.id = :id
              and notification.recipient.id = :recipientUserId
              and notification.readAt is null
            """)
    int markOneUnread(
            @Param("id") Long id,
            @Param("recipientUserId") Long recipientUserId,
            @Param("readAt") Instant readAt);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update Notification notification
            set notification.readAt = :readAt
            where notification.recipient.id = :recipientUserId
              and notification.readAt is null
            """)
    int markAllUnread(
            @Param("recipientUserId") Long recipientUserId,
            @Param("readAt") Instant readAt);
}
