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

    @Enumerated(EnumType.STRING)
    @Column(name = "participant_type", nullable = false)
    private ParticipantType participantType = ParticipantType.ADULT;

    public enum ParticipantType { ADULT, CHILD }

    protected BookingParticipant() {}

    public BookingParticipant(String fullName, String phone, boolean isPrimaryContact) {
        this(fullName, phone, isPrimaryContact, ParticipantType.ADULT);
    }

    public BookingParticipant(String fullName, String phone, boolean isPrimaryContact,
                              ParticipantType participantType) {
        this.fullName = fullName;
        this.phone = phone;
        this.isPrimaryContact = isPrimaryContact;
        this.participantType = participantType == null ? ParticipantType.ADULT : participantType;
    }

    public void setBooking(Booking booking) { this.booking = booking; }

    public UUID getId() { return id; }
    public Booking getBooking() { return booking; }
    public String getFullName() { return fullName; }
    public String getPhone() { return phone; }
    public boolean isPrimaryContact() { return isPrimaryContact; }
    public ParticipantType getParticipantType() {
        return participantType == null ? ParticipantType.ADULT : participantType;
    }
}
