package com.vietkhampha.tourservice.dto;

import com.vietkhampha.tourservice.entity.Tour;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;
public class TourResponse {

    private String id;
    private String name;
    private String description;
    private Tour.Destination destination;
    private String coverImageUrl;
    private List<String> images;
    private BigDecimal basePrice;
    private String tourGuideId;
    private List<Tour.ItineraryDay> itinerary;
    private String status;
    private BigDecimal avgRating;
    private Integer reviewCount;
    private Instant createdAt;
    private Instant updatedAt;

    public static TourResponse from(Tour tour) {
        TourResponse res = new TourResponse();
        res.id = tour.getId();
        res.name = tour.getName();
        res.description = tour.getDescription();
        res.destination = tour.getDestination();
        res.coverImageUrl = tour.getCoverImageUrl();
        res.images = tour.getImages();
        res.basePrice = tour.getBasePrice();
        res.tourGuideId = tour.getTourGuideId();
        res.itinerary = tour.getItinerary();
        res.status = tour.getStatus().name();
        res.avgRating = tour.getAvgRating();
        res.reviewCount = tour.getReviewCount();
        res.createdAt = tour.getCreatedAt();
        res.updatedAt = tour.getUpdatedAt();
        return res;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public Tour.Destination getDestination() { return destination; }
    public String getCoverImageUrl() { return coverImageUrl; }
    public List<String> getImages() { return images; }
    public BigDecimal getBasePrice() { return basePrice; }
    public String getTourGuideId() { return tourGuideId; }
    public List<Tour.ItineraryDay> getItinerary() { return itinerary; }
    public String getStatus() { return status; }
    public BigDecimal getAvgRating() { return avgRating; }
    public Integer getReviewCount() { return reviewCount; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}