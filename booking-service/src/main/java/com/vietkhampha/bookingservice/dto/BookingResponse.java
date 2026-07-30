package com.vietkhampha.bookingservice.dto;

import com.vietkhampha.bookingservice.entity.Booking;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public class BookingResponse {
    private UUID bookingId;
    private UUID customerId;
    private UUID tourSlotId;
    private BigDecimal totalAmount;
    private String status;
    private Instant holdExpiresAt;

    public static BookingResponse from(Booking b) {
        BookingResponse dto = new BookingResponse();
        dto.bookingId = b.getId();
        dto.customerId = b.getCustomerId();
        dto.tourSlotId = b.getTourSlotId();
        dto.totalAmount = b.getTotalAmount();
        dto.status = b.getStatus().name();
        dto.holdExpiresAt = b.getHoldExpiresAt();
        return dto;
    }

    public UUID getBookingId() { return bookingId; }
    public UUID getCustomerId() { return customerId; }
    public UUID getTourSlotId() { return tourSlotId; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public String getStatus() { return status; }
    public Instant getHoldExpiresAt() { return holdExpiresAt; }
}
