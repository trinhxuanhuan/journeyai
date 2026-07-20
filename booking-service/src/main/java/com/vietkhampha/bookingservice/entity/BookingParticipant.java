package com.vietkhampha.bookingservice.entity;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "booking_participants")
public class BookingParticipant {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    private String phone;

    @Column(name = "is_primary_contact", nullable = false)
    private boolean isPrimaryContact;

    protected BookingParticipant() {}

    public BookingParticipant(String fullName, String phone, boolean isPrimaryContact) {
        this.fullName = fullName;
        this.phone = phone;
        this.isPrimaryContact = isPrimaryContact;
    }

    public void setBooking(Booking booking) { this.booking = booking; }

    public UUID getId() { return id; }
    public Booking getBooking() { return booking; }
    public String getFullName() { return fullName; }
    public String getPhone() { return phone; }
    public boolean isPrimaryContact() { return isPrimaryContact; }
}