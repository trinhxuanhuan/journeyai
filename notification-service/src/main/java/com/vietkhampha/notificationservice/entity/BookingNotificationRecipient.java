package com.vietkhampha.notificationservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "booking_notification_recipients")
public class BookingNotificationRecipient {

    @Id
    @Column(name = "booking_id")
    private UUID bookingId;

    @Column(name = "auth_user_id", nullable = false)
    private UUID authUserId;

    @Column(name = "tour_id")
    private String tourId;

    @Column(name = "booking_status", nullable = false, length = 48)
    private String bookingStatus;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "reminder_sent_at")
    private Instant reminderSentAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected BookingNotificationRecipient() {
    }

    public BookingNotificationRecipient(UUID bookingId, UUID authUserId) {
        this.bookingId = bookingId;
        this.authUserId = authUserId;
        this.bookingStatus = "PENDING";
    }

    public void applySnapshot(UUID authUserId, String tourId, String bookingStatus,
                              LocalDate startDate, LocalDate endDate) {
        this.authUserId = authUserId;
        if (tourId != null && !tourId.isBlank()) this.tourId = tourId;
        if (bookingStatus != null && !bookingStatus.isBlank()) this.bookingStatus = bookingStatus;
        if (startDate != null) this.startDate = startDate;
        if (endDate != null) this.endDate = endDate;
        if (!"CONFIRMED".equals(this.bookingStatus)) this.reminderSentAt = null;
        this.updatedAt = Instant.now();
    }

    public void markReminderSent(Instant now) {
        reminderSentAt = now;
        updatedAt = now;
    }

    public UUID getBookingId() { return bookingId; }
    public UUID getAuthUserId() { return authUserId; }
    public String getTourId() { return tourId; }
    public String getBookingStatus() { return bookingStatus; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate() { return endDate; }
    public Instant getReminderSentAt() { return reminderSentAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
