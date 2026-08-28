package com.vietkhampha.bookingservice.dto;

import com.vietkhampha.bookingservice.entity.TourSlot;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;

import java.math.BigDecimal;
import java.time.LocalDate;

public class UpdateDepartureRequest {
    private LocalDate startDate;
    private LocalDate endDate;

    @Min(value = 1, message = "Suc chua toi thieu 1")
    private Integer capacity;

    private String guideId;

    @DecimalMin(value = "0.0", inclusive = false, message = "Gia thay the phai lon hon 0")
    private BigDecimal priceOverride;

    private TourSlot.Status status;

    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
    public Integer getCapacity() { return capacity; }
    public void setCapacity(Integer capacity) { this.capacity = capacity; }
    public String getGuideId() { return guideId; }
    public void setGuideId(String guideId) { this.guideId = guideId; }
    public BigDecimal getPriceOverride() { return priceOverride; }
    public void setPriceOverride(BigDecimal priceOverride) { this.priceOverride = priceOverride; }
    public TourSlot.Status getStatus() { return status; }
    public void setStatus(TourSlot.Status status) { this.status = status; }
}
