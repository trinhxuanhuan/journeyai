package com.vietkhampha.bookingservice.entity;

import jakarta.persistence.*;
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

    @Column(name = "max_capacity", nullable = false)
    private int maxCapacity;

    @Column(name = "booked_count", nullable = false)
    private int bookedCount = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.OPEN;

    @Version
    private int version;

    public enum Status {
        OPEN, CLOSED
    }

    protected TourSlot() {}

    public TourSlot(String tourId, LocalDate departureDate, int maxCapacity) {
        this.tourId = tourId;
        this.departureDate = departureDate;
        this.maxCapacity = maxCapacity;
    }

    public UUID getId() { return id; }
    public String getTourId() { return tourId; }
    public LocalDate getDepartureDate() { return departureDate; }
    public int getMaxCapacity() { return maxCapacity; }
    public int getBookedCount() { return bookedCount; }
    public Status getStatus() { return status; }
    public int getVersion() { return version; }

    public int getAvailableSlots() {
        return maxCapacity - bookedCount;
    }

    public boolean hasCapacityFor(int participantCount) {
        return status == Status.OPEN && getAvailableSlots() >= participantCount;
    }

    public void reserve(int participantCount) {
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
}