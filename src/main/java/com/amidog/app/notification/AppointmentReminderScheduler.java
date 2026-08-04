package com.amidog.app.notification;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class AppointmentReminderScheduler {

    private final NotificationService notifications;

    public AppointmentReminderScheduler(
            NotificationService notifications) {
        this.notifications = notifications;
    }

    @Scheduled(
            cron = "${amidog.notifications.reminder.cron:0 5 * * * *}",
            zone = "${amidog.clinic-zone:America/Santiago}")
    public void scan() {
        notifications.createAppointmentReminders();
    }
}
