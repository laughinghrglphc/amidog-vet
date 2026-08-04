package com.amidog.app.notification;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "amidog.notifications.reminder")
@Validated
public record NotificationReminderProperties(
        boolean enabled,
        @Min(1) @Max(167) int windowStartHours,
        @Min(2) @Max(168) int windowEndHours,
        @Min(1) @Max(500) int batchSize
) {

    public NotificationReminderProperties {
        if (windowEndHours <= windowStartHours) {
            throw new IllegalArgumentException(
                    "reminder window end must be after its start");
        }
    }

    int approximateLeadTimeHours() {
        return windowStartHours
                + ((windowEndHours - windowStartHours) / 2);
    }
}
