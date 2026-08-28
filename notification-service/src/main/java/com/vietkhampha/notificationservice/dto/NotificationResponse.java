package com.vietkhampha.notificationservice.dto;

import com.vietkhampha.notificationservice.entity.Notification;

import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(
        UUID id,
        String type,
        String category,
        String title,
        String message,
        String actionUrl,
        String referenceType,
        String referenceId,
        boolean read,
        Instant readAt,
        Instant createdAt
) {
    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getNotificationType(),
                notification.getCategory().name(),
                notification.getTitle(),
                notification.getMessage(),
                notification.getActionUrl(),
                notification.getReferenceType(),
                notification.getReferenceId(),
                notification.getReadAt() != null,
                notification.getReadAt(),
                notification.getCreatedAt()
        );
    }
}
