package com.vietkhampha.bookingservice.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Min;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public class CreateBookingRequest {
    private String tourId;
    private UUID departureId;

    /** Legacy alias for departureId. */
    @Deprecated
    private UUID tourSlotId;

    private LocalDate requestedStartDate;
    private boolean guideOptionSelected;

    @Min(value = 0, message = "So phong don khong duoc am")
    private int singleRoomCount;

    @NotEmpty(message = "Phai co it nhat 1 nguoi tham gia")
    @Valid
    private List<ParticipantDto> participants;

    /** Deprecated: AI itinerary is an independent domain and is no longer attached to a booking. */
    @Deprecated
    private String generatedItineraryId;

    protected CreateBookingRequest() {}

    public String getTourId() { return tourId; }
    public void setTourId(String tourId) { this.tourId = tourId; }
    public UUID getDepartureId() { return departureId == null ? tourSlotId : departureId; }
    public void setDepartureId(UUID departureId) { this.departureId = departureId; }
    public UUID getTourSlotId() { return getDepartureId(); }
    public void setTourSlotId(UUID tourSlotId) { this.tourSlotId = tourSlotId; }
    public LocalDate getRequestedStartDate() { return requestedStartDate; }
    public void setRequestedStartDate(LocalDate requestedStartDate) { this.requestedStartDate = requestedStartDate; }
    public boolean isGuideOptionSelected() { return guideOptionSelected; }
    public void setGuideOptionSelected(boolean guideOptionSelected) { this.guideOptionSelected = guideOptionSelected; }
    public int getSingleRoomCount() { return singleRoomCount; }
    public void setSingleRoomCount(int singleRoomCount) { this.singleRoomCount = singleRoomCount; }
    public List<ParticipantDto> getParticipants() { return participants; }
    public void setParticipants(List<ParticipantDto> participants) { this.participants = participants; }
    public String getGeneratedItineraryId() { return generatedItineraryId; }
    public void setGeneratedItineraryId(String generatedItineraryId) { this.generatedItineraryId = generatedItineraryId; }
}
