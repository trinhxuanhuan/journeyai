package com.vietkhampha.bookingservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "idempotency_keys")
@IdClass(IdempotencyKeyId.class)
public class IdempotencyKey {

    public enum RecordState {
        LEGACY_EXPIRED,
        PROCESSING,
        COMPLETED
    }

    @Id
    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Id
    @Column(name = "key", nullable = false, length = 255)
    private String key;

    @Column(name = "booking_id")
    private UUID bookingId;

    @Enumerated(EnumType.STRING)
    @Column(name = "record_state", nullable = false, length = 32)
    private RecordState recordState;

    @Column(name = "request_hash", length = 64)
    private String requestHash;

    @Column(name = "hash_version", length = 32)
    private String hashVersion;

    @Column(name = "response_snapshot", columnDefinition = "TEXT")
    private String responseSnapshot;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    protected IdempotencyKey() {
    }

    public void complete(UUID bookingId, String responseSnapshot) {
        if (recordState != RecordState.PROCESSING) {
            throw new IllegalStateException("Only a PROCESSING idempotency record can be completed");
        }
        this.bookingId = bookingId;
        this.responseSnapshot = responseSnapshot;
        this.recordState = RecordState.COMPLETED;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public String getKey() {
        return key;
    }

    public UUID getBookingId() {
        return bookingId;
    }

    public RecordState getRecordState() {
        return recordState;
    }

    public String getRequestHash() {
        return requestHash;
    }

    public String getHashVersion() {
        return hashVersion;
    }

    public String getResponseSnapshot() {
        return responseSnapshot;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}
