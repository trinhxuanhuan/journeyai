package com.vietkhampha.bookingservice.dto;

import com.vietkhampha.bookingservice.entity.Booking;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public class BookingResponse {
    private UUID bookingId;
    private UUID customerId;
    private UUID tourSlotId;
    private UUID departureId;
    private String tourId;
    private String bookingType;
    private LocalDate startDate;
    private LocalDate endDate;
    private int participantCount;
    private String priceModel;
    private BigDecimal unitPrice;
    private BigDecimal totalAmount;
    private String commercialSnapshot;
    private String assignedGuideId;
    private boolean guideOptionSelected;
    private int singleRoomCount;
    private List<ParticipantResponse> participants;
    private String status;
    private Instant holdExpiresAt;

    public static BookingResponse from(Booking b) {
        BookingResponse dto = new BookingResponse();
        dto.bookingId = b.getId();
        dto.customerId = b.getCustomerId();
        dto.tourSlotId = b.getTourSlotId();
        dto.departureId = b.getDepartureId();
        dto.tourId = b.getTourId();
        dto.bookingType = b.getBookingType().name();
        dto.startDate = b.getStartDate();
        dto.endDate = b.getEndDate();
        dto.participantCount = b.getParticipantCount();
        dto.priceModel = b.getPriceModel().name();
        dto.unitPrice = b.getUnitPrice();
        dto.totalAmount = b.getTotalAmount();
        dto.commercialSnapshot = b.getCommercialSnapshot();
        dto.assignedGuideId = b.getAssignedGuideId();
        dto.guideOptionSelected = b.isGuideOptionSelected();
        dto.singleRoomCount = b.getSingleRoomCount();
        dto.participants = b.getParticipants().stream()
                .map(participant -> new ParticipantResponse(
                        participant.getFullName(), participant.getPhone(), participant.isPrimaryContact(),
                        participant.getParticipantType().name()
                ))
                .toList();
        dto.status = b.getStatus().name();
        dto.holdExpiresAt = b.getHoldExpiresAt();
        return dto;
    }

    public UUID getBookingId() { return bookingId; }
    public UUID getCustomerId() { return customerId; }
    public UUID getTourSlotId() { return tourSlotId; }
    public UUID getDepartureId() { return departureId; }
    public String getTourId() { return tourId; }
    public String getBookingType() { return bookingType; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate() { return endDate; }
    public int getParticipantCount() { return participantCount; }
    public String getPriceModel() { return priceModel; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public String getCommercialSnapshot() { return commercialSnapshot; }
    public String getAssignedGuideId() { return assignedGuideId; }
    public boolean isGuideOptionSelected() { return guideOptionSelected; }
    public int getSingleRoomCount() { return singleRoomCount; }
    public List<ParticipantResponse> getParticipants() { return participants; }
    public String getStatus() { return status; }
    public Instant getHoldExpiresAt() { return holdExpiresAt; }

    public record ParticipantResponse(String fullName, String phone, boolean primaryContact, String participantType) {}
}
