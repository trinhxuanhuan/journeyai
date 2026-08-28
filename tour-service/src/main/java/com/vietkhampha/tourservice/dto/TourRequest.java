package com.vietkhampha.tourservice.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import com.vietkhampha.tourservice.entity.Tour;

import java.math.BigDecimal;
import java.time.LocalTime;
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

    private Tour.TourType tourType;
    private Tour.PriceModel priceModel;
    private String departureLocation;
    private String meetingPoint;
    private LocalTime meetingTime;

    @Min(value = 1, message = "Quy mo nhom toi thieu phai lon hon 0")
    private Integer minGroupSize;

    @Min(value = 1, message = "Quy mo nhom toi da phai lon hon 0")
    private Integer maxGroupSize;

    private Tour.GuideMode guideMode;

    @DecimalMin(value = "0.0", message = "Phu thu huong dan vien khong duoc am")
    private BigDecimal optionalGuidePrice;

    @Min(value = 1, message = "So ngay phai lon hon 0")
    private Integer durationDays;

    @Min(value = 0, message = "So dem khong duoc am")
    private Integer durationNights;

    private List<@NotBlank String> included;
    private List<@NotBlank String> excluded;

    @Valid
    private PackageDetailsDto packageDetails;

    @Valid
    private ChildPolicyDto childPolicy;

    @DecimalMin(value = "0.0", message = "Phu thu phong don khong duoc am")
    private BigDecimal singleRoomSupplement;

    @Valid
    private List<CancellationRuleDto> cancellationPolicy;

    /** Legacy compatibility only. New tours assign guides on Departure/Booking. */
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
    public Tour.TourType getTourType() { return tourType; }
    public void setTourType(Tour.TourType tourType) { this.tourType = tourType; }
    public Tour.PriceModel getPriceModel() { return priceModel; }
    public void setPriceModel(Tour.PriceModel priceModel) { this.priceModel = priceModel; }
    public String getDepartureLocation() { return departureLocation; }
    public void setDepartureLocation(String departureLocation) { this.departureLocation = departureLocation; }
    public String getMeetingPoint() { return meetingPoint; }
    public void setMeetingPoint(String meetingPoint) { this.meetingPoint = meetingPoint; }
    public LocalTime getMeetingTime() { return meetingTime; }
    public void setMeetingTime(LocalTime meetingTime) { this.meetingTime = meetingTime; }
    public Integer getMinGroupSize() { return minGroupSize; }
    public void setMinGroupSize(Integer minGroupSize) { this.minGroupSize = minGroupSize; }
    public Integer getMaxGroupSize() { return maxGroupSize; }
    public void setMaxGroupSize(Integer maxGroupSize) { this.maxGroupSize = maxGroupSize; }
    public Tour.GuideMode getGuideMode() { return guideMode; }
    public void setGuideMode(Tour.GuideMode guideMode) { this.guideMode = guideMode; }
    public BigDecimal getOptionalGuidePrice() { return optionalGuidePrice; }
    public void setOptionalGuidePrice(BigDecimal optionalGuidePrice) { this.optionalGuidePrice = optionalGuidePrice; }
    public Integer getDurationDays() { return durationDays; }
    public void setDurationDays(Integer durationDays) { this.durationDays = durationDays; }
    public Integer getDurationNights() { return durationNights; }
    public void setDurationNights(Integer durationNights) { this.durationNights = durationNights; }
    public List<String> getIncluded() { return included; }
    public void setIncluded(List<String> included) { this.included = included; }
    public List<String> getExcluded() { return excluded; }
    public void setExcluded(List<String> excluded) { this.excluded = excluded; }
    public PackageDetailsDto getPackageDetails() { return packageDetails; }
    public void setPackageDetails(PackageDetailsDto packageDetails) { this.packageDetails = packageDetails; }
    public ChildPolicyDto getChildPolicy() { return childPolicy; }
    public void setChildPolicy(ChildPolicyDto childPolicy) { this.childPolicy = childPolicy; }
    public BigDecimal getSingleRoomSupplement() { return singleRoomSupplement; }
    public void setSingleRoomSupplement(BigDecimal singleRoomSupplement) { this.singleRoomSupplement = singleRoomSupplement; }
    public List<CancellationRuleDto> getCancellationPolicy() { return cancellationPolicy; }
    public void setCancellationPolicy(List<CancellationRuleDto> cancellationPolicy) { this.cancellationPolicy = cancellationPolicy; }
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

    public static class PackageDetailsDto {
        private List<@NotBlank String> accommodation;
        private List<@NotBlank String> transport;
        private List<@NotBlank String> meals;
        private List<@NotBlank String> tickets;
        private List<@NotBlank String> insurance;

        public List<String> getAccommodation() { return accommodation; }
        public void setAccommodation(List<String> accommodation) { this.accommodation = accommodation; }
        public List<String> getTransport() { return transport; }
        public void setTransport(List<String> transport) { this.transport = transport; }
        public List<String> getMeals() { return meals; }
        public void setMeals(List<String> meals) { this.meals = meals; }
        public List<String> getTickets() { return tickets; }
        public void setTickets(List<String> tickets) { this.tickets = tickets; }
        public List<String> getInsurance() { return insurance; }
        public void setInsurance(List<String> insurance) { this.insurance = insurance; }
    }

    public static class ChildPolicyDto {
        private String description;

        @DecimalMin(value = "0.0", message = "Ty le gia tre em khong duoc am")
        @DecimalMax(value = "100.0", message = "Ty le gia tre em khong duoc vuot qua 100")
        private BigDecimal pricePercentage;

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public BigDecimal getPricePercentage() { return pricePercentage; }
        public void setPricePercentage(BigDecimal pricePercentage) { this.pricePercentage = pricePercentage; }
    }

    public static class CancellationRuleDto {
        @Min(value = 0, message = "So ngay truoc khoi hanh khong duoc am")
        private int minimumDaysBeforeDeparture;

        @Min(value = 0, message = "Ty le hoan tien khong duoc am")
        @Max(value = 100, message = "Ty le hoan tien khong duoc vuot qua 100")
        private int refundPercentage;

        public int getMinimumDaysBeforeDeparture() { return minimumDaysBeforeDeparture; }
        public void setMinimumDaysBeforeDeparture(int minimumDaysBeforeDeparture) {
            this.minimumDaysBeforeDeparture = minimumDaysBeforeDeparture;
        }
        public int getRefundPercentage() { return refundPercentage; }
        public void setRefundPercentage(int refundPercentage) { this.refundPercentage = refundPercentage; }
    }
}
