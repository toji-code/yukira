package com.yukira.backend.dto.notification;

import java.time.ZonedDateTime;

public record InvestorInAppNotificationDto(
    Long id,
    Long eventId,
    String title,
    String message,
    String notificationCategory,
    String severity,
    Boolean isRead,
    ZonedDateTime readAt,
    Boolean isArchived,
    String actionUrl,
    ZonedDateTime createdAt
) {}

