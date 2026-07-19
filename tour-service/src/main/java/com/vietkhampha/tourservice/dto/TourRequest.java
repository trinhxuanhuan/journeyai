package com.vietkhampha.tourservice.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.List;

public class TourRequest {

    @NotBlank(message = "Ten tour khong duoc de trong")
    private String name;

    @NotBlank(message = "Mo ta khong duoc de trong")
    private String description;

    @NotNull(message = "Diem den khong duoc de trong")
    @Valid
    private DestinationDto destination;

    private String coverImageUrl;
    private List<String> images;

    @NotNull(message = "Gia khong duoc de trong")
    @DecimalMin(value = "0.0", inclusive = false, message = "Gia phai lon hon 0")
    private BigDecimal basePrice;

    @NotBlank(message = "Phai chon huong dan vien")
    private String tourGuideId;

    @NotEmpty(message = "Lich trinh khong duoc de trong")
    @Valid
    private List<ItineraryDayDto> itinerary;

    protected TourRequest() {}

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public DestinationDto getDestination() { return destination; }
    public void setDestination(DestinationDto destination) { this.destination = destination; }
    public String getCoverImageUrl() { return coverImageUrl; }
    public void setCoverImageUrl(String coverImageUrl) { this.coverImageUrl = coverImageUrl; }
    public List<String> getImages() { return images; }
    public void setImages(List<String> images) { this.images = images; }
    public BigDecimal getBasePrice() { return basePrice; }
    public void setBasePrice(BigDecimal basePrice) { this.basePrice = basePrice; }
    public String getTourGuideId() { return tourGuideId; }
    public void setTourGuideId(String tourGuideId) { this.tourGuideId = tourGuideId; }
    public List<ItineraryDayDto> getItinerary() { return itinerary; }
    public void setItinerary(List<ItineraryDayDto> itinerary) { this.itinerary = itinerary; }

    public static class DestinationDto {
        @NotBlank(message = "Tinh/thanh pho khong duoc de trong")
        private String province;
        @NotNull
        @Valid
        private GeoDto geo;

        public String getProvince() { return province; }
        public void setProvince(String province) { this.province = province; }
        public GeoDto getGeo() { return geo; }
        public void setGeo(GeoDto geo) { this.geo = geo; }
    }

    public static class GeoDto {
        @NotNull
        private Double lat;
        @NotNull
        private Double lng;

        public Double getLat() { return lat; }
        public void setLat(Double lat) { this.lat = lat; }
        public Double getLng() { return lng; }
        public void setLng(Double lng) { this.lng = lng; }
    }

    public static class ItineraryDayDto {
        @NotNull
        private Integer dayNumber;
        @NotBlank(message = "Tieu de ngay khong duoc de trong")
        private String title;
        @NotEmpty(message = "Moi ngay phai co it nhat 1 hoat dong")
        @Valid
        private List<ActivityDto> activities;

        public Integer getDayNumber() { return dayNumber; }
        public void setDayNumber(Integer dayNumber) { this.dayNumber = dayNumber; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public List<ActivityDto> getActivities() { return activities; }
        public void setActivities(List<ActivityDto> activities) { this.activities = activities; }
    }

    public static class ActivityDto {
        @NotBlank
        private String time;
        @NotBlank(message = "Mo ta hoat dong khong duoc de trong")
        private String description;
        @Valid
        private GeoDto location;

        public String getTime() { return time; }
        public void setTime(String time) { this.time = time; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public GeoDto getLocation() { return location; }
        public void setLocation(GeoDto location) { this.location = location; }
    }
}