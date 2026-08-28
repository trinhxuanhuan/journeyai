package com.vietkhampha.notificationservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notifications")
public class Notification {

    public enum Category { BOOKING, PAYMENT, DEPARTURE, SYSTEM }

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "auth_user_id", nullable = false)
    private UUID authUserId;

    @Column(name = "event_id", nullable = false, unique = true)
    private UUID eventId;

    @Column(name = "notification_type", nullable = false, length = 64)
    private String notificationType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Category category;

    @Column(nullable = false, length = 160)
    private String title;

    @Column(nullable = false, length = 1000)
    private String message;

    @Column(name = "action_url", length = 500)
    private String actionUrl;

    @Column(name = "reference_type", length = 32)
    private String referenceType;

    @Column(name = "reference_id")
    private String referenceId;

    @Column(name = "read_at")
    private Instant readAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected Notification() {
    }

    public Notification(UUID authUserId, UUID eventId, String notificationType, Category category,
                        String title, String message, String actionUrl,
                        String referenceType, String referenceId) {
        this.authUserId = authUserId;
        this.eventId = eventId;
        this.notificationType = notificationType;
        this.category = category;
        this.title = title;
        this.message = message;
        this.actionUrl = actionUrl;
        this.referenceType = referenceType;
        this.referenceId = referenceId;
    }

    public void markRead(Instant now) {
        if (readAt == null) readAt = now;
    }

    public UUID getId() { return id; }
    public UUID getAuthUserId() { return authUserId; }
    public UUID getEventId() { return eventId; }
    public String getNotificationType() { return notificationType; }
    public Category getCategory() { return category; }
    public String getTitle() { return title; }
    public String getMessage() { return message; }
    public String getActionUrl() { return actionUrl; }
    public String getReferenceType() { return referenceType; }
    public String getReferenceId() { return referenceId; }
    public Instant getReadAt() { return readAt; }
    public Instant getCreatedAt() { return createdAt; }
}
