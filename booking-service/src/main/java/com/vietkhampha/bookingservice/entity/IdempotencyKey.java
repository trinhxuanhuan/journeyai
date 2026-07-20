package com.vietkhampha.bookingservice.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "idempotency_keys")
public class IdempotencyKey {

    @Id
    private String key;

    @Column(name = "booking_id", nullable = false)
    private UUID bookingId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    protected IdempotencyKey() {}

    public IdempotencyKey(String key, UUID bookingId) {
        this.key = key;
        this.bookingId = bookingId;
        this.expiresAt = Instant.now().plusSeconds(24 * 60 * 60); // TTL 24h — UC-D01
    }

    public String getKey() { return key; }
    public UUID getBookingId() { return bookingId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
}