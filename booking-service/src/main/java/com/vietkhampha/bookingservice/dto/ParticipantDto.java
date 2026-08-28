package com.vietkhampha.bookingservice.dto;

import jakarta.validation.constraints.NotBlank;
import com.vietkhampha.bookingservice.entity.BookingParticipant;

public class ParticipantDto {
    @NotBlank(message = "Ho ten khong duoc de trong")
    private String fullName;
    private String phone;
    private boolean isPrimaryContact;
    private BookingParticipant.ParticipantType participantType = BookingParticipant.ParticipantType.ADULT;

    protected ParticipantDto() {}

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public boolean isPrimaryContact() { return isPrimaryContact; }
    public void setPrimaryContact(boolean primaryContact) { isPrimaryContact = primaryContact; }
    public BookingParticipant.ParticipantType getParticipantType() {
        return participantType == null ? BookingParticipant.ParticipantType.ADULT : participantType;
    }
    public void setParticipantType(BookingParticipant.ParticipantType participantType) {
        this.participantType = participantType;
    }
}
