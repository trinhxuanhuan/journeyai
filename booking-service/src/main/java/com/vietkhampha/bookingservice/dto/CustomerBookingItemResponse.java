package com.vietkhampha.bookingservice.dto;

import com.vietkhampha.bookingservice.entity.Booking;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public class CustomerBookingItemResponse {

    private final UUID bookingId;
    private final UUID tourSlotId;
    private final UUID departureId;
    private final String tourId;
    private final String bookingType;
    private final LocalDate startDate;
    private final LocalDate endDate;
    private final int participantCount;
    private final BigDecimal totalAmount;
    private final String status;
    private final Instant holdExpiresAt;
    private final Instant createdAt;
    private final Instant updatedAt;

    private CustomerBookingItemResponse(
            UUID bookingId,
            UUID tourSlotId,
            UUID departureId,
            String tourId,
            String bookingType,
            LocalDate startDate,
            LocalDate endDate,
            int participantCount,
            BigDecimal totalAmount,
            String status,
            Instant holdExpiresAt,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.bookingId = bookingId;
        this.tourSlotId = tourSlotId;
        this.departureId = departureId;
        this.tourId = tourId;
        this.bookingType = bookingType;
        this.startDate = startDate;
        this.endDate = endDate;
        this.participantCount = participantCount;
        this.totalAmount = totalAmount;
        this.status = status;
        this.holdExpiresAt = holdExpiresAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static CustomerBookingItemResponse from(Booking booking) {
        return new CustomerBookingItemResponse(
                booking.getId(),
                booking.getTourSlotId(),
                booking.getDepartureId(),
                booking.getTourId(),
                booking.getBookingType().name(),
                booking.getStartDate(),
                booking.getEndDate(),
                booking.getParticipantCount(),
                booking.getTotalAmount(),
                booking.getStatus().name(),
                booking.getHoldExpiresAt(),
                booking.getCreatedAt(),
                booking.getUpdatedAt()
        );
    }

    public UUID getBookingId() { return bookingId; }
    public UUID getTourSlotId() { return tourSlotId; }
    public UUID getDepartureId() { return departureId; }
    public String getTourId() { return tourId; }
    public String getBookingType() { return bookingType; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate() { return endDate; }
    public int getParticipantCount() { return participantCount; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public String getStatus() { return status; }
    public Instant getHoldExpiresAt() { return holdExpiresAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
