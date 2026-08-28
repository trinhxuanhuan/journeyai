package com.vietkhampha.tourservice.dto;

import com.vietkhampha.tourservice.entity.Tour;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
public class TourResponse {

    private String id;
    private String name;
    private String description;
    private Tour.Destination destination;
    private String coverImageUrl;
    private List<String> images;
    private BigDecimal basePrice;
    private String tourType;
    private String priceModel;
    private String departureLocation;
    private String meetingPoint;
    private LocalTime meetingTime;
    private Integer minGroupSize;
    private Integer maxGroupSize;
    private String guideMode;
    private BigDecimal optionalGuidePrice;
    private Integer durationDays;
    private Integer durationNights;
    private List<String> included;
    private List<String> excluded;
    private Tour.PackageDetails packageDetails;
    private Tour.ChildPolicy childPolicy;
    private BigDecimal singleRoomSupplement;
    private List<Tour.CancellationRule> cancellationPolicy;
    @Deprecated
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
        res.tourType = tour.getTourType().name();
        res.priceModel = tour.getPriceModel().name();
        res.departureLocation = tour.getDepartureLocation();
        res.meetingPoint = tour.getMeetingPoint();
        res.meetingTime = tour.getMeetingTime();
        res.minGroupSize = tour.getMinGroupSize();
        res.maxGroupSize = tour.getMaxGroupSize();
        res.guideMode = tour.getGuideMode().name();
        res.optionalGuidePrice = tour.getOptionalGuidePrice();
        res.durationDays = tour.getDurationDays();
        res.durationNights = tour.getDurationNights();
        res.included = tour.getIncluded();
        res.excluded = tour.getExcluded();
        res.packageDetails = tour.getPackageDetails();
        res.childPolicy = tour.getChildPolicy();
        res.singleRoomSupplement = tour.getSingleRoomSupplement();
        res.cancellationPolicy = tour.getCancellationPolicy();
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
    public String getTourType() { return tourType; }
    public String getPriceModel() { return priceModel; }
    public String getDepartureLocation() { return departureLocation; }
    public String getMeetingPoint() { return meetingPoint; }
    public LocalTime getMeetingTime() { return meetingTime; }
    public Integer getMinGroupSize() { return minGroupSize; }
    public Integer getMaxGroupSize() { return maxGroupSize; }
    public String getGuideMode() { return guideMode; }
    public BigDecimal getOptionalGuidePrice() { return optionalGuidePrice; }
    public Integer getDurationDays() { return durationDays; }
    public Integer getDurationNights() { return durationNights; }
    public List<String> getIncluded() { return included; }
    public List<String> getExcluded() { return excluded; }
    public Tour.PackageDetails getPackageDetails() { return packageDetails; }
    public Tour.ChildPolicy getChildPolicy() { return childPolicy; }
    public BigDecimal getSingleRoomSupplement() { return singleRoomSupplement; }
    public List<Tour.CancellationRule> getCancellationPolicy() { return cancellationPolicy; }
    public String getTourGuideId() { return tourGuideId; }
    public List<Tour.ItineraryDay> getItinerary() { return itinerary; }
    public String getStatus() { return status; }
    public BigDecimal getAvgRating() { return avgRating; }
    public Integer getReviewCount() { return reviewCount; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
