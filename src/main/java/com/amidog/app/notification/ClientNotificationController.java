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
@RequestMapping("/api/v1/me/notifications")
public class ClientNotificationController {

    private final NotificationService notifications;

    public ClientNotificationController(
            NotificationService notifications) {
        this.notifications = notifications;
    }

    @GetMapping
    List<NotificationResponse> list(
            @AuthenticationPrincipal AccountPrincipal principal) {
        return notifications.listClient(principal);
    }

    @PatchMapping("/{id}/read")
    NotificationResponse markRead(
            @AuthenticationPrincipal AccountPrincipal principal,
            @PathVariable long id) {
        return notifications.markClientRead(principal, id);
    }

    @PostMapping("/read-all")
    ReadAllResponse markAllRead(
            @AuthenticationPrincipal AccountPrincipal principal) {
        return notifications.markAllClientRead(principal);
    }
}
