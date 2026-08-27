package com.vietkhampha.bookingservice.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public class CreateDepartureRequest {

    @NotNull(message = "Ngay khoi hanh khong duoc de trong")
    @FutureOrPresent(message = "Ngay khoi hanh khong duoc trong qua khu")
    private LocalDate startDate;

    @NotNull(message = "Ngay ket thuc khong duoc de trong")
    private LocalDate endDate;

    @Min(value = 1, message = "Suc chua toi thieu 1")
    private int capacity;

    @NotBlank(message = "Departure phai co huong dan vien")
    private String guideId;

    @DecimalMin(value = "0.0", inclusive = false, message = "Gia thay the phai lon hon 0")
    private BigDecimal priceOverride;

    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
    public int getCapacity() { return capacity; }
    public void setCapacity(int capacity) { this.capacity = capacity; }
    public String getGuideId() { return guideId; }
    public void setGuideId(String guideId) { this.guideId = guideId; }
    public BigDecimal getPriceOverride() { return priceOverride; }
    public void setPriceOverride(BigDecimal priceOverride) { this.priceOverride = priceOverride; }
}
