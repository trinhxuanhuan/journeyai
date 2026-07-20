package com.vietkhampha.bookingservice.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public class CreateBookingRequest {
    @NotNull(message = "tourSlotId khong duoc de trong")
    private UUID tourSlotId;

    @NotEmpty(message = "Phai co it nhat 1 nguoi tham gia")
    @Valid
    private List<ParticipantDto> participants;

    private String generatedItineraryId; // nullable — API_CONTRACT.md §5

    protected CreateBookingRequest() {}

    public UUID getTourSlotId() { return tourSlotId; }
    public void setTourSlotId(UUID tourSlotId) { this.tourSlotId = tourSlotId; }
    public List<ParticipantDto> getParticipants() { return participants; }
    public void setParticipants(List<ParticipantDto> participants) { this.participants = participants; }
    public String getGeneratedItineraryId() { return generatedItineraryId; }
    public void setGeneratedItineraryId(String generatedItineraryId) { this.generatedItineraryId = generatedItineraryId; }
}