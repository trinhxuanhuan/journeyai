package com.vietkhampha.bookingservice.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
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

    @Column(name = "tour_slot_id", nullable = false)
    private UUID tourSlotId;

    @Column(name = "participant_count", nullable = false)
    private int participantCount;

    @Column(name = "total_amount", nullable = false)
    private BigDecimal totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.PENDING;

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
        PENDING, CONFIRMED, EXPIRED, PAYMENT_FAILED, CANCELLED, COMPLETED
    }

    protected Booking() {}

    public Booking(UUID customerId, UUID tourSlotId, int participantCount, BigDecimal totalAmount) {
        this.customerId = customerId;
        this.tourSlotId = tourSlotId;
        this.participantCount = participantCount;
        this.totalAmount = totalAmount;
        this.holdExpiresAt = Instant.now().plusSeconds(15 * 60); // UC-D01: giữ chỗ 15 phút
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
    public int getParticipantCount() { return participantCount; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public Status getStatus() { return status; }
    public String getGeneratedItineraryId() { return generatedItineraryId; }
    public Instant getHoldExpiresAt() { return holdExpiresAt; }
    public List<BookingParticipant> getParticipants() { return participants; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}