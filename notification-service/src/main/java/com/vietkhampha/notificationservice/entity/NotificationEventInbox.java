package com.vietkhampha.notificationservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notification_event_inbox")
public class NotificationEventInbox {

    public enum Status { RECEIVED, WAITING, PROCESSED, IGNORED, FAILED }

    @Id
    @Column(name = "event_id")
    private UUID eventId;

    @Column(name = "source_topic", nullable = false, length = 64)
    private String sourceTopic;

    @Column(name = "event_type", nullable = false, length = 96)
    private String eventType;

    @Column(name = "aggregate_id")
    private String aggregateId;

    @Column(name = "event_payload", nullable = false, columnDefinition = "TEXT")
    private String eventPayload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private Status status = Status.RECEIVED;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    @Column(name = "last_error", length = 1000)
    private String lastError;

    @Column(name = "received_at", nullable = false, updatable = false)
    private Instant receivedAt = Instant.now();

    @Column(name = "processed_at")
    private Instant processedAt;

    protected NotificationEventInbox() {
    }

    public NotificationEventInbox(UUID eventId, String sourceTopic, String eventType,
                                  String aggregateId, String eventPayload) {
        this.eventId = eventId;
        this.sourceTopic = sourceTopic;
        this.eventType = eventType;
        this.aggregateId = aggregateId;
        this.eventPayload = eventPayload;
    }

    public boolean isTerminal() {
        return status == Status.PROCESSED || status == Status.IGNORED || status == Status.FAILED;
    }

    public void markProcessed(Instant now) {
        status = Status.PROCESSED;
        processedAt = now;
        nextAttemptAt = null;
        lastError = null;
    }

    public void markIgnored(Instant now, String reason) {
        status = Status.IGNORED;
        processedAt = now;
        nextAttemptAt = null;
        lastError = truncate(reason);
    }

    public void defer(Instant nextAttemptAt, String reason) {
        attemptCount++;
        status = Status.WAITING;
        this.nextAttemptAt = nextAttemptAt;
        lastError = truncate(reason);
    }

    public void markFailed(Instant now, String reason) {
        attemptCount++;
        status = Status.FAILED;
        processedAt = now;
        nextAttemptAt = null;
        lastError = truncate(reason);
    }

    private String truncate(String value) {
        if (value == null) return null;
        return value.length() <= 1000 ? value : value.substring(0, 1000);
    }

    public UUID getEventId() { return eventId; }
    public String getSourceTopic() { return sourceTopic; }
    public String getEventType() { return eventType; }
    public String getAggregateId() { return aggregateId; }
    public String getEventPayload() { return eventPayload; }
    public Status getStatus() { return status; }
    public int getAttemptCount() { return attemptCount; }
    public Instant getNextAttemptAt() { return nextAttemptAt; }
    public String getLastError() { return lastError; }
    public Instant getReceivedAt() { return receivedAt; }
    public Instant getProcessedAt() { return processedAt; }
}
