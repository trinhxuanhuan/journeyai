package com.vietkhampha.paymentservice.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "refunds")
public class Refund {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "payment_id", nullable = false)
    private UUID paymentId;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(nullable = false)
    private int percentage;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.PENDING;

    @Column(name = "gateway_refund_ref")
    private String gatewayRefundRef;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "completed_at")
    private Instant completedAt;

    public enum Status { PENDING, SUCCESS, MANUAL_REQUIRED }

    protected Refund() {}

    public Refund(UUID paymentId, BigDecimal amount, int percentage) {
        this.paymentId = paymentId;
        this.amount = amount;
        this.percentage = percentage;
    }

    public void markSuccess(String gatewayRefundRef) {
        this.status = Status.SUCCESS;
        this.gatewayRefundRef = gatewayRefundRef;
        this.completedAt = Instant.now();
    }

    public void markManualRequired() {
        this.status = Status.MANUAL_REQUIRED;
        this.completedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getPaymentId() { return paymentId; }
    public BigDecimal getAmount() { return amount; }
    public int getPercentage() { return percentage; }
    public Status getStatus() { return status; }
    public String getGatewayRefundRef() { return gatewayRefundRef; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getCompletedAt() { return completedAt; }
}