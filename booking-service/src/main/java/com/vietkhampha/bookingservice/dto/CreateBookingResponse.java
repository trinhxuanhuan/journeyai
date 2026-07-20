package com.vietkhampha.bookingservice.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public class CreateBookingResponse {
    private UUID bookingId;
    private String status;
    private BigDecimal totalAmount;
    private Instant holdExpiresAt;

    public CreateBookingResponse(UUID bookingId, String status, BigDecimal totalAmount, Instant holdExpiresAt) {
        this.bookingId = bookingId;
        this.status = status;
        this.totalAmount = totalAmount;
        this.holdExpiresAt = holdExpiresAt;
    }

    public UUID getBookingId() { return bookingId; }
    public String getStatus() { return status; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public Instant getHoldExpiresAt() { return holdExpiresAt; }
}