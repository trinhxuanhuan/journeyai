package com.vietkhampha.tourservice.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Document(collection = "tours")
public class Tour {

    @Id
    private String id;

    private String name;
    private String description;
    private Destination destination;
    private String coverImageUrl;
    private List<String> images = new ArrayList<>();
    private BigDecimal basePrice;
    private String tourGuideId;
    private List<ItineraryDay> itinerary = new ArrayList<>();

    private Status status = Status.ACTIVE;

    // Denormalized — cập nhật khi có review mới (Epic G, chưa làm) — ERD.md §6
    private BigDecimal avgRating = BigDecimal.ZERO;
    private Integer reviewCount = 0;

    private Instant createdAt = Instant.now();
    private Instant updatedAt = Instant.now();

    public enum Status {
        ACTIVE, INACTIVE
    }

    protected Tour() {
    }

    public Tour(String name, String description, Destination destination, String coverImageUrl,
                List<String> images, BigDecimal basePrice, String tourGuideId, List<ItineraryDay> itinerary) {
        this.name = name;
        this.description = description;
        this.destination = destination;
        this.coverImageUrl = coverImageUrl;
        this.images = images;
        this.basePrice = basePrice;
        this.tourGuideId = tourGuideId;
        this.itinerary = itinerary;
    }

    public void applyUpdate(String name, String description, Destination destination, String coverImageUrl,
                            List<String> images, BigDecimal basePrice, String tourGuideId, List<ItineraryDay> itinerary) {
        this.name = name;
        this.description = description;
        this.destination = destination;
        this.coverImageUrl = coverImageUrl;
        this.images = images;
        this.basePrice = basePrice;
        this.tourGuideId = tourGuideId;
        this.itinerary = itinerary;
        this.updatedAt = Instant.now();
    }

    public void markInactive() {
        this.status = Status.INACTIVE;
        this.updatedAt = Instant.now();
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public Destination getDestination() { return destination; }
    public String getCoverImageUrl() { return coverImageUrl; }
    public List<String> getImages() { return images; }
    public BigDecimal getBasePrice() { return basePrice; }
    public String getTourGuideId() { return tourGuideId; }
    public List<ItineraryDay> getItinerary() { return itinerary; }
    public Status getStatus() { return status; }
    public BigDecimal getAvgRating() { return avgRating; }
    public Integer getReviewCount() { return reviewCount; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }


    public static class Destination {
        private String province;
        private Geo geo;

        protected Destination() {}
        public Destination(String province, Geo geo) {
            this.province = province;
            this.geo = geo;
        }
        public String getProvince() { return province; }
        public Geo getGeo() { return geo; }
    }

    public static class Geo {
        private double lat;
        private double lng;

        protected Geo() {}
        public Geo(double lat, double lng) {
            this.lat = lat;
            this.lng = lng;
        }
        public double getLat() { return lat; }
        public double getLng() { return lng; }
    }

    public static class ItineraryDay {
        private int dayNumber;
        private String title;
        private List<Activity> activities = new ArrayList<>();

        protected ItineraryDay() {}
        public ItineraryDay(int dayNumber, String title, List<Activity> activities) {
            this.dayNumber = dayNumber;
            this.title = title;
            this.activities = activities;
        }
        public int getDayNumber() { return dayNumber; }
        public String getTitle() { return title; }
        public List<Activity> getActivities() { return activities; }
    }

    public static class Activity {
        private String time;
        private String description;
        private Geo location;

        protected Activity() {}
        public Activity(String time, String description, Geo location) {
            this.time = time;
            this.description = description;
            this.location = location;
        }
        public String getTime() { return time; }
        public String getDescription() { return description; }
        public Geo getLocation() { return location; }
    }
}
