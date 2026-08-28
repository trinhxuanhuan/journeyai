package com.vietkhampha.notificationservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notification_email_deliveries")
public class NotificationEmailDelivery {

    public enum Status { PENDING, WAITING_RECIPIENT, SENT, SKIPPED, FAILED }

    @Id
    @GeneratedValue
    private UUID id;

    @OneToOne(optional = false)
    @JoinColumn(name = "notification_id", nullable = false, unique = true)
    private Notification notification;

    @Column(name = "recipient_email", length = 320)
    private String recipientEmail;

    @Column(nullable = false, length = 200)
    private String subject;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Status status;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    @Column(name = "last_error", length = 1000)
    private String lastError;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Column(name = "sent_at")
    private Instant sentAt;

    protected NotificationEmailDelivery() {
    }

    public static NotificationEmailDelivery pending(Notification notification, String email,
                                                    String subject, String body, Instant now) {
        NotificationEmailDelivery delivery = base(notification, subject, body);
        delivery.recipientEmail = email;
        delivery.status = Status.PENDING;
        delivery.nextAttemptAt = now;
        return delivery;
    }

    public static NotificationEmailDelivery waitingForRecipient(Notification notification,
                                                                 String subject, String body, Instant now) {
        NotificationEmailDelivery delivery = base(notification, subject, body);
        delivery.status = Status.WAITING_RECIPIENT;
        delivery.nextAttemptAt = now.plus(Duration.ofMinutes(5));
        delivery.lastError = "Chưa có địa chỉ email của người nhận";
        return delivery;
    }

    public static NotificationEmailDelivery skipped(Notification notification, String subject,
                                                     String body, String reason) {
        NotificationEmailDelivery delivery = base(notification, subject, body);
        delivery.status = Status.SKIPPED;
        delivery.lastError = delivery.truncate(reason);
        return delivery;
    }

    private static NotificationEmailDelivery base(Notification notification, String subject, String body) {
        NotificationEmailDelivery delivery = new NotificationEmailDelivery();
        delivery.notification = notification;
        delivery.subject = subject;
        delivery.body = body;
        return delivery;
    }

    public void resolveRecipient(String email, Instant now) {
        recipientEmail = email;
        status = Status.PENDING;
        nextAttemptAt = now;
        lastError = null;
        updatedAt = now;
    }

    public void markSent(Instant now) {
        status = Status.SENT;
        sentAt = now;
        nextAttemptAt = null;
        lastError = null;
        updatedAt = now;
    }

    public void markSkipped(String reason, Instant now) {
        status = Status.SKIPPED;
        nextAttemptAt = null;
        lastError = truncate(reason);
        updatedAt = now;
    }

    public void recordFailure(String reason, Instant now, int maxAttempts) {
        attemptCount++;
        lastError = truncate(reason);
        updatedAt = now;
        if (attemptCount >= maxAttempts) {
            status = Status.FAILED;
            nextAttemptAt = null;
            return;
        }
        long delayMinutes = Math.min(60L, 1L << Math.min(attemptCount, 6));
        nextAttemptAt = now.plus(Duration.ofMinutes(delayMinutes));
    }

    private String truncate(String value) {
        if (value == null) return null;
        return value.length() <= 1000 ? value : value.substring(0, 1000);
    }

    public UUID getId() { return id; }
    public Notification getNotification() { return notification; }
    public String getRecipientEmail() { return recipientEmail; }
    public String getSubject() { return subject; }
    public String getBody() { return body; }
    public Status getStatus() { return status; }
    public int getAttemptCount() { return attemptCount; }
    public Instant getNextAttemptAt() { return nextAttemptAt; }
    public String getLastError() { return lastError; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getSentAt() { return sentAt; }
}
