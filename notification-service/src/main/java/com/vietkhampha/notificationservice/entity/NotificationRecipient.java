package com.vietkhampha.notificationservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Entity
@Table(name = "notification_recipients")
public class NotificationRecipient {

    @Id
    @Column(name = "auth_user_id")
    private UUID authUserId;

    @Column(length = 320)
    private String email;

    @Column(name = "full_name")
    private String fullName;

    @Column(name = "email_notifications_enabled", nullable = false)
    private boolean emailNotificationsEnabled = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected NotificationRecipient() {
    }

    public NotificationRecipient(UUID authUserId) {
        this.authUserId = authUserId;
    }

    public void syncIdentity(String email, String fullName) {
        if (email != null && !email.isBlank()) this.email = email.trim().toLowerCase(Locale.ROOT);
        if (fullName != null && !fullName.isBlank()) this.fullName = fullName.trim();
        updatedAt = Instant.now();
    }

    public void updateEmailNotificationsEnabled(boolean enabled) {
        emailNotificationsEnabled = enabled;
        updatedAt = Instant.now();
    }

    public UUID getAuthUserId() { return authUserId; }
    public String getEmail() { return email; }
    public String getFullName() { return fullName; }
    public boolean isEmailNotificationsEnabled() { return emailNotificationsEnabled; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
