package com.vietkhampha.bookingservice.dto;

import com.vietkhampha.bookingservice.entity.Booking;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public class AdminBookingItemDto {
    private UUID bookingId;
    private UUID customerId;
    private UUID tourSlotId;
    private UUID departureId;
    private String tourId;
    private String bookingType;
    private LocalDate startDate;
    private int participantCount;
    private BigDecimal totalAmount;
    private String status;
    private Instant createdAt;

    public static AdminBookingItemDto from(Booking b) {
        AdminBookingItemDto dto = new AdminBookingItemDto();
        dto.bookingId = b.getId();
        dto.customerId = b.getCustomerId();
        dto.tourSlotId = b.getTourSlotId();
        dto.departureId = b.getDepartureId();
        dto.tourId = b.getTourId();
        dto.bookingType = b.getBookingType().name();
        dto.startDate = b.getStartDate();
        dto.participantCount = b.getParticipantCount();
        dto.totalAmount = b.getTotalAmount();
        dto.status = b.getStatus().name();
        dto.createdAt = b.getCreatedAt();
        return dto;
    }

    public UUID getBookingId() { return bookingId; }
    public UUID getCustomerId() { return customerId; }
    public UUID getTourSlotId() { return tourSlotId; }
    public UUID getDepartureId() { return departureId; }
    public String getTourId() { return tourId; }
    public String getBookingType() { return bookingType; }
    public LocalDate getStartDate() { return startDate; }
    public int getParticipantCount() { return participantCount; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public String getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
}
