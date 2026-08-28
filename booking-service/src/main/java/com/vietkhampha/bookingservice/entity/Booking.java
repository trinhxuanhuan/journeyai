package com.vietkhampha.bookingservice.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "bookings")
public class Booking {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "tour_slot_id")
    private UUID tourSlotId;

    @Column(name = "tour_id", nullable = false)
    private String tourId;

    @Enumerated(EnumType.STRING)
    @Column(name = "booking_type", nullable = false)
    private BookingType bookingType = BookingType.GROUP;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "participant_count", nullable = false)
    private int participantCount;

    @Column(name = "total_amount", nullable = false)
    private BigDecimal totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "price_model", nullable = false)
    private PriceModel priceModel = PriceModel.PER_PERSON;

    @Column(name = "unit_price", nullable = false)
    private BigDecimal unitPrice;

    @Column(name = "commercial_snapshot", columnDefinition = "TEXT")
    private String commercialSnapshot;

    @Column(name = "cancellation_policy_snapshot", columnDefinition = "TEXT")
    private String cancellationPolicySnapshot;

    @Column(name = "assigned_guide_id")
    private String assignedGuideId;

    @Column(name = "guide_option_selected", nullable = false)
    private boolean guideOptionSelected;

    @Column(name = "single_room_count", nullable = false)
    private int singleRoomCount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.PENDING;

    @Deprecated
    @Column(name = "generated_itinerary_id")
    private String generatedItineraryId;

    @Column(name = "hold_expires_at", nullable = false)
    private Instant holdExpiresAt;

    @OneToMany(mappedBy = "booking", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<BookingParticipant> participants = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public enum Status {
        PENDING, CONFIRMED, EXPIRED, PAYMENT_FAILED, CANCELLED, COMPLETED, PAYMENT_REVIEW_REQUIRED
    }

    public enum BookingType { GROUP, PRIVATE }
    public enum PriceModel { PER_PERSON, PER_GROUP }

    protected Booking() {}

    public Booking(UUID customerId, UUID tourSlotId, int participantCount, BigDecimal totalAmount) {
        this.customerId = customerId;
        this.tourSlotId = tourSlotId;
        this.participantCount = participantCount;
        this.totalAmount = totalAmount;
        this.unitPrice = participantCount > 0
                ? totalAmount.divide(BigDecimal.valueOf(participantCount), 2, java.math.RoundingMode.HALF_UP)
                : totalAmount;
        this.holdExpiresAt = Instant.now().plusSeconds(15 * 60); // UC-D01: giữ chỗ 15 phút
    }

    public Booking(UUID customerId, String tourId, BookingType bookingType, UUID departureId,
                   LocalDate startDate, LocalDate endDate, int participantCount,
                   PriceModel priceModel, BigDecimal unitPrice, BigDecimal totalAmount,
                   String commercialSnapshot, String cancellationPolicySnapshot,
                   boolean guideOptionSelected, int singleRoomCount) {
        this.customerId = customerId;
        this.tourId = tourId;
        this.bookingType = bookingType;
        this.tourSlotId = departureId;
        this.startDate = startDate;
        this.endDate = endDate;
        this.participantCount = participantCount;
        this.priceModel = priceModel;
        this.unitPrice = unitPrice;
        this.totalAmount = totalAmount;
        this.commercialSnapshot = commercialSnapshot;
        this.cancellationPolicySnapshot = cancellationPolicySnapshot;
        this.guideOptionSelected = guideOptionSelected;
        this.singleRoomCount = singleRoomCount;
        this.holdExpiresAt = Instant.now().plusSeconds(15 * 60);
    }

    public void addParticipant(BookingParticipant participant) {
        participant.setBooking(this);
        this.participants.add(participant);
    }

    public void setStatus(Status status) {
        this.status = status;
        this.updatedAt = Instant.now();
    }
    public void setGeneratedItineraryId(String generatedItineraryId) {
        this.generatedItineraryId = generatedItineraryId;
    }

    public UUID getId() { return id; }
    public UUID getCustomerId() { return customerId; }
    public UUID getTourSlotId() { return tourSlotId; }
    public UUID getDepartureId() { return tourSlotId; }
    public String getTourId() { return tourId; }
    public BookingType getBookingType() { return bookingType == null ? BookingType.GROUP : bookingType; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate() { return endDate; }
    public int getParticipantCount() { return participantCount; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public PriceModel getPriceModel() { return priceModel == null ? PriceModel.PER_PERSON : priceModel; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public String getCommercialSnapshot() { return commercialSnapshot; }
    public String getCancellationPolicySnapshot() { return cancellationPolicySnapshot; }
    public String getAssignedGuideId() { return assignedGuideId; }
    public boolean isGuideOptionSelected() { return guideOptionSelected; }
    public int getSingleRoomCount() { return singleRoomCount; }
    public Status getStatus() { return status; }
    public String getGeneratedItineraryId() { return generatedItineraryId; }
    public Instant getHoldExpiresAt() { return holdExpiresAt; }
    public List<BookingParticipant> getParticipants() { return participants; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public boolean usesSharedCapacity() {
        return getBookingType() == BookingType.GROUP && tourSlotId != null;
    }

    public void assignGuide(String guideId) {
        this.assignedGuideId = guideId;
        this.updatedAt = Instant.now();
    }
}
