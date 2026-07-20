package com.vietkhampha.bookingservice.dto;

import jakarta.validation.constraints.NotBlank;

public class ParticipantDto {
    @NotBlank(message = "Ho ten khong duoc de trong")
    private String fullName;
    private String phone;
    private boolean isPrimaryContact;

    protected ParticipantDto() {}

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public boolean isPrimaryContact() { return isPrimaryContact; }
    public void setPrimaryContact(boolean primaryContact) { isPrimaryContact = primaryContact; }
}
