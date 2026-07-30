package com.vietkhampha.paymentservice.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payments")
public class Payment {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "booking_id", nullable = false)
    private UUID bookingId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Gateway gateway;

    @Column(name = "gateway_transaction_ref", unique = true)
    private String gatewayTransactionRef;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(nullable = false)
    private String currency = "VND";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.INITIATED;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "completed_at")
    private Instant completedAt;

    public enum Gateway { VNPAY, STRIPE }
    public enum Status { INITIATED, SUCCESS, FAILED, CANCELLED }

    protected Payment() {}

    public Payment(UUID bookingId, Gateway gateway, BigDecimal amount) {
        this.bookingId = bookingId;
        this.gateway = gateway;
        this.amount = amount;
    }

    public void assignTransactionRef(String ref) {
        this.gatewayTransactionRef = ref;
    }

    public void markSuccess() {
        this.status = Status.SUCCESS;
        this.completedAt = Instant.now();
    }

    public void markFailed() {
        this.status = Status.FAILED;
        this.completedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getBookingId() { return bookingId; }
    public Gateway getGateway() { return gateway; }
    public String getGatewayTransactionRef() { return gatewayTransactionRef; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public Status getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getCompletedAt() { return completedAt; }
}
