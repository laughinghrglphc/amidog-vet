package com.amidog.app.notification;

import com.amidog.app.auth.AccountPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static com.amidog.app.notification.NotificationDtos.NotificationResponse;
import static com.amidog.app.notification.NotificationDtos.ReadAllResponse;

@RestController
@RequestMapping("/api/v1/admin/notifications")
public class AdminNotificationController {

    private final NotificationService notifications;

    public AdminNotificationController(
            NotificationService notifications) {
        this.notifications = notifications;
    }

    @GetMapping
    List<NotificationResponse> list(
            @AuthenticationPrincipal AccountPrincipal principal) {
        return notifications.listAdmin(principal);
    }

    @PatchMapping("/{id}/read")
    NotificationResponse markRead(
            @AuthenticationPrincipal AccountPrincipal principal,
            @PathVariable long id) {
        return notifications.markAdminRead(principal, id);
    }

    @PostMapping("/read-all")
    ReadAllResponse markAllRead(
            @AuthenticationPrincipal AccountPrincipal principal) {
        return notifications.markAllAdminRead(principal);
    }
}
