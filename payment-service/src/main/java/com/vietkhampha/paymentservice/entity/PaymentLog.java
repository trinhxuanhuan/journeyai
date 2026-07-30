package com.vietkhampha.paymentservice.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payment_logs")
public class PaymentLog {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "payment_id", nullable = false)
    private UUID paymentId;

    @Column(name = "event_source", nullable = false)
    private String eventSource; // REDIRECT, WEBHOOK_IPN

    @Column(name = "raw_payload", columnDefinition = "TEXT", nullable = false)
    private String rawPayload;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt = Instant.now();

    protected PaymentLog() {}

    public PaymentLog(UUID paymentId, String eventSource, String rawPayload) {
        this.paymentId = paymentId;
        this.eventSource = eventSource;
        this.rawPayload = rawPayload;
    }

    public UUID getId() { return id; }
    public UUID getPaymentId() { return paymentId; }
    public String getEventSource() { return eventSource; }
    public String getRawPayload() { return rawPayload; }
    public Instant getReceivedAt() { return receivedAt; }
}