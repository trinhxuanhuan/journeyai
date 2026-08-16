package com.vietkhampha.bookingservice.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public class CreateBookingResponse {
    private UUID bookingId;
    private String status;
    private BigDecimal totalAmount;
    private Instant holdExpiresAt;

    @JsonCreator
    public CreateBookingResponse(
            @JsonProperty("bookingId") UUID bookingId,
            @JsonProperty("status") String status,
            @JsonProperty("totalAmount") BigDecimal totalAmount,
            @JsonProperty("holdExpiresAt") Instant holdExpiresAt
    ) {
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
