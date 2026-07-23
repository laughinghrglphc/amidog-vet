package com.amidog.app.notification;

import java.time.Instant;

public final class NotificationDtos {

    private NotificationDtos() {
    }

    public record NotificationResponse(
            Long id,
            NotificationType type,
            String title,
            String body,
            Long reservationId,
            Instant createdAt,
            boolean unread) {

        public static NotificationResponse from(
                Notification notification) {
            return new NotificationResponse(
                    notification.getId(),
                    notification.getType(),
                    notification.getTitle(),
                    notification.getBody(),
                    notification.getReservation() == null
                            ? null
                            : notification.getReservation().getId(),
                    notification.getCreatedAt(),
                    notification.getReadAt() == null);
        }
    }

    public record ReadAllResponse(int markedRead) {
    }
}
