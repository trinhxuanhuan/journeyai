package com.vietkhampha.tourservice.dto;

import com.vietkhampha.tourservice.document.TourSearchDocument;

import java.math.BigDecimal;
import java.time.Instant;

public class TourSearchItemDto {

    private String tourId;
    private String name;
    private String coverImageUrl;
    private BigDecimal basePrice;
    private BigDecimal avgRating;
    private Instant nearestDepartureDate;
    private boolean hasAvailableSlot;

    public static TourSearchItemDto from(TourSearchDocument doc) {
        TourSearchItemDto dto = new TourSearchItemDto();
        dto.tourId = doc.getId();
        dto.name = doc.getName();
        dto.coverImageUrl = doc.getCoverImageUrl();
        dto.basePrice = doc.getBasePrice();
        dto.avgRating = doc.getAvgRating();
        dto.nearestDepartureDate = doc.getNearestDepartureDate();
        dto.hasAvailableSlot = doc.isHasAvailableSlot();
        return dto;
    }

    public String getTourId() { return tourId; }
    public String getName() { return name; }
    public String getCoverImageUrl() { return coverImageUrl; }
    public BigDecimal getBasePrice() { return basePrice; }
    public BigDecimal getAvgRating() { return avgRating; }
    public Instant getNearestDepartureDate() { return nearestDepartureDate; }
    public boolean isHasAvailableSlot() { return hasAvailableSlot; }
}
