package com.vietkhampha.bookingservice.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "tour_slots", uniqueConstraints = @UniqueConstraint(columnNames = {"tour_id", "departure_date"}))
public class TourSlot {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "tour_id", nullable = false)
    private String tourId;

    @Column(name = "departure_date", nullable = false)
    private LocalDate departureDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "max_capacity", nullable = false)
    private int maxCapacity;

    @Column(name = "booked_count", nullable = false)
    private int bookedCount = 0;

    @Column(name = "guide_id")
    private String guideId;

    @Column(name = "price_override")
    private BigDecimal priceOverride;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.OPEN;

    @Version
    private int version;

    public enum Status {
        OPEN, CLOSED, CANCELLED, COMPLETED
    }

    protected TourSlot() {}

    public TourSlot(String tourId, LocalDate departureDate, int maxCapacity) {
        this(tourId, departureDate, departureDate, maxCapacity, null, null, Status.OPEN);
    }

    public TourSlot(String tourId, LocalDate departureDate, LocalDate endDate, int maxCapacity,
                    String guideId, BigDecimal priceOverride, Status status) {
        this.tourId = tourId;
        this.departureDate = departureDate;
        this.endDate = endDate;
        this.maxCapacity = maxCapacity;
        this.guideId = guideId;
        this.priceOverride = priceOverride;
        this.status = status == null ? Status.OPEN : status;
    }

    public UUID getId() { return id; }
    public String getTourId() { return tourId; }
    public LocalDate getDepartureDate() { return departureDate; }
    public LocalDate getStartDate() { return departureDate; }
    public LocalDate getEndDate() { return endDate == null ? departureDate : endDate; }
    public int getMaxCapacity() { return maxCapacity; }
    public int getBookedCount() { return bookedCount; }
    public int getReservedSeats() { return bookedCount; }
    public String getGuideId() { return guideId; }
    public BigDecimal getPriceOverride() { return priceOverride; }
    public Status getStatus() { return status; }
    public int getVersion() { return version; }

    public int getAvailableSlots() {
        return Math.max(0, maxCapacity - bookedCount);
    }

    public String getEffectiveStatus() {
        return status == Status.OPEN && getAvailableSlots() == 0 ? "FULL" : status.name();
    }

    public boolean hasCapacityFor(int participantCount) {
        return status == Status.OPEN && getAvailableSlots() >= participantCount;
    }

    public void reserve(int participantCount) {
        if (participantCount < 1 || !hasCapacityFor(participantCount)) {
            throw new IllegalStateException("Departure khong du cho de giu " + participantCount + " cho");
        }
        this.bookedCount += participantCount;
    }

    public void release(int participantCount) {
        if (participantCount > this.bookedCount) {
            throw new IllegalStateException(
                    "Khong the release " + participantCount + " cho slot " + id
                            + " - bookedCount hien tai chi la " + this.bookedCount
                            + ". Co the day la double-release hoac loi logic goi sai."
            );
        }
        this.bookedCount -= participantCount;
    }

    public void applyUpdate(LocalDate startDate, LocalDate endDate, Integer capacity,
                            String guideId, BigDecimal priceOverride, Status status) {
        LocalDate resolvedStartDate = startDate == null ? this.departureDate : startDate;
        LocalDate resolvedEndDate = endDate == null ? getEndDate() : endDate;
        int resolvedCapacity = capacity == null ? this.maxCapacity : capacity;
        Status resolvedStatus = status == null ? this.status : status;
        String resolvedGuideId = guideId == null ? this.guideId : guideId.trim();
        if (resolvedEndDate.isBefore(resolvedStartDate) || resolvedCapacity < bookedCount || resolvedCapacity < 1) {
            throw new IllegalArgumentException("Cau hinh Departure khong hop le");
        }
        if (resolvedStatus == Status.OPEN && (resolvedGuideId == null || resolvedGuideId.isBlank())) {
            throw new IllegalArgumentException("Departure OPEN phai co huong dan vien");
        }
        if (priceOverride != null && priceOverride.signum() <= 0) {
            throw new IllegalArgumentException("Gia thay the phai lon hon 0");
        }
        this.departureDate = resolvedStartDate;
        this.endDate = resolvedEndDate;
        this.maxCapacity = resolvedCapacity;
        if (guideId != null) this.guideId = resolvedGuideId;
        if (priceOverride != null) this.priceOverride = priceOverride;
        this.status = resolvedStatus;
    }
}
