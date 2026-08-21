package com.vietkhampha.paymentservice.entity;

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
@Table(name = "payment_idempotency_keys")
@IdClass(PaymentIdempotencyKeyId.class)
public class PaymentIdempotencyKey {

    public enum RecordState {
        PROCESSING,
        COMPLETED
    }

    @Id
    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Id
    @Column(name = "key", nullable = false, length = 255)
    private String key;

    @Column(name = "payment_id")
    private UUID paymentId;

    @Column(name = "booking_id")
    private UUID bookingId;

    @Enumerated(EnumType.STRING)
    @Column(name = "record_state", nullable = false, length = 32)
    private RecordState recordState;

    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @Column(name = "hash_version", nullable = false, length = 32)
    private String hashVersion;

    @Column(name = "response_snapshot", columnDefinition = "TEXT")
    private String responseSnapshot;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "replay_expires_at", nullable = false)
    private Instant replayExpiresAt;

    @Column(name = "key_expires_at", nullable = false)
    private Instant keyExpiresAt;

    protected PaymentIdempotencyKey() {
    }

    public void complete(UUID bookingId, UUID paymentId, String responseSnapshot) {
        if (recordState != RecordState.PROCESSING) {
            throw new IllegalStateException("Only a PROCESSING payment idempotency record can be completed");
        }
        this.bookingId = bookingId;
        this.paymentId = paymentId;
        this.responseSnapshot = responseSnapshot;
        this.recordState = RecordState.COMPLETED;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public String getKey() {
        return key;
    }

    public UUID getPaymentId() {
        return paymentId;
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

    public Instant getReplayExpiresAt() {
        return replayExpiresAt;
    }

    public Instant getKeyExpiresAt() {
        return keyExpiresAt;
    }
}
