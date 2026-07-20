package com.vietkhampha.bookingservice.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public class CreateSlotRequest {

    private String tourId;

    @NotNull(message = "Ngay khoi hanh khong duoc de trong")
    @Future(message = "Ngay khoi hanh phai trong tuong lai")
    private LocalDate departureDate;

    @Min(value = 1, message = "Suc chua toi thieu 1")
    private int maxCapacity;

    protected CreateSlotRequest() {}

    public String getTourId() { return tourId; }
    public void setTourId(String tourId) { this.tourId = tourId; }
    public LocalDate getDepartureDate() { return departureDate; }
    public void setDepartureDate(LocalDate departureDate) { this.departureDate = departureDate; }
    public int getMaxCapacity() { return maxCapacity; }
    public void setMaxCapacity(int maxCapacity) { this.maxCapacity = maxCapacity; }
}