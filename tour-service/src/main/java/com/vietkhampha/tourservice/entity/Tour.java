package com.vietkhampha.tourservice.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;
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
    private TourType tourType = TourType.GROUP;
    private PriceModel priceModel = PriceModel.PER_PERSON;
    private String departureLocation;
    private String meetingPoint;
    private LocalTime meetingTime;
    private Integer minGroupSize = 1;
    private Integer maxGroupSize = 30;
    private GuideMode guideMode = GuideMode.INCLUDED;
    private BigDecimal optionalGuidePrice = BigDecimal.ZERO;
    private Integer durationDays = 1;
    private Integer durationNights = 0;
    private List<String> included = new ArrayList<>();
    private List<String> excluded = new ArrayList<>();
    private PackageDetails packageDetails = new PackageDetails();
    private ChildPolicy childPolicy = new ChildPolicy();
    private BigDecimal singleRoomSupplement = BigDecimal.ZERO;
    private List<CancellationRule> cancellationPolicy = new ArrayList<>();

    /**
     * Legacy field kept temporarily so existing Mongo documents and clients remain readable.
     * Concrete guides are assigned to a Departure (GROUP) or Booking (PRIVATE).
     */
    @Deprecated
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

    public enum TourType { GROUP, PRIVATE }
    public enum PriceModel { PER_PERSON, PER_GROUP }
    public enum GuideMode { INCLUDED, OPTIONAL, NONE }

    protected Tour() {
    }

    public Tour(String name, String description, Destination destination, String coverImageUrl,
                List<String> images, BigDecimal basePrice, TourType tourType, PriceModel priceModel,
                String departureLocation, String meetingPoint, LocalTime meetingTime,
                Integer minGroupSize, Integer maxGroupSize, GuideMode guideMode,
                BigDecimal optionalGuidePrice, Integer durationDays, Integer durationNights,
                List<String> included, List<String> excluded, PackageDetails packageDetails,
                ChildPolicy childPolicy, BigDecimal singleRoomSupplement,
                List<CancellationRule> cancellationPolicy, String legacyTourGuideId,
                List<ItineraryDay> itinerary) {
        this.name = name;
        this.description = description;
        this.destination = destination;
        this.coverImageUrl = coverImageUrl;
        this.images = safeList(images);
        this.basePrice = basePrice;
        this.tourType = tourType;
        this.priceModel = priceModel;
        this.departureLocation = departureLocation;
        this.meetingPoint = meetingPoint;
        this.meetingTime = meetingTime;
        this.minGroupSize = minGroupSize;
        this.maxGroupSize = maxGroupSize;
        this.guideMode = guideMode;
        this.optionalGuidePrice = optionalGuidePrice;
        this.durationDays = durationDays;
        this.durationNights = durationNights;
        this.included = safeList(included);
        this.excluded = safeList(excluded);
        this.packageDetails = packageDetails;
        this.childPolicy = childPolicy;
        this.singleRoomSupplement = singleRoomSupplement;
        this.cancellationPolicy = safeList(cancellationPolicy);
        this.tourGuideId = legacyTourGuideId;
        this.itinerary = safeList(itinerary);
    }

    public void applyUpdate(String name, String description, Destination destination, String coverImageUrl,
                            List<String> images, BigDecimal basePrice, TourType tourType, PriceModel priceModel,
                            String departureLocation, String meetingPoint, LocalTime meetingTime,
                            Integer minGroupSize, Integer maxGroupSize, GuideMode guideMode,
                            BigDecimal optionalGuidePrice, Integer durationDays, Integer durationNights,
                            List<String> included, List<String> excluded, PackageDetails packageDetails,
                            ChildPolicy childPolicy, BigDecimal singleRoomSupplement,
                            List<CancellationRule> cancellationPolicy, String legacyTourGuideId,
                            List<ItineraryDay> itinerary) {
        this.name = name;
        this.description = description;
        this.destination = destination;
        this.coverImageUrl = coverImageUrl;
        this.images = safeList(images);
        this.basePrice = basePrice;
        this.tourType = tourType;
        this.priceModel = priceModel;
        this.departureLocation = departureLocation;
        this.meetingPoint = meetingPoint;
        this.meetingTime = meetingTime;
        this.minGroupSize = minGroupSize;
        this.maxGroupSize = maxGroupSize;
        this.guideMode = guideMode;
        this.optionalGuidePrice = optionalGuidePrice;
        this.durationDays = durationDays;
        this.durationNights = durationNights;
        this.included = safeList(included);
        this.excluded = safeList(excluded);
        this.packageDetails = packageDetails;
        this.childPolicy = childPolicy;
        this.singleRoomSupplement = singleRoomSupplement;
        this.cancellationPolicy = safeList(cancellationPolicy);
        this.tourGuideId = legacyTourGuideId;
        this.itinerary = safeList(itinerary);
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
    public TourType getTourType() { return tourType == null ? TourType.GROUP : tourType; }
    public PriceModel getPriceModel() { return priceModel == null ? PriceModel.PER_PERSON : priceModel; }
    public String getDepartureLocation() {
        return departureLocation == null && destination != null ? destination.getProvince() : departureLocation;
    }
    public String getMeetingPoint() { return meetingPoint == null ? getDepartureLocation() : meetingPoint; }
    public LocalTime getMeetingTime() { return meetingTime; }
    public Integer getMinGroupSize() { return minGroupSize == null ? 1 : minGroupSize; }
    public Integer getMaxGroupSize() { return maxGroupSize == null ? 30 : maxGroupSize; }
    public GuideMode getGuideMode() { return guideMode == null ? GuideMode.INCLUDED : guideMode; }
    public BigDecimal getOptionalGuidePrice() {
        return optionalGuidePrice == null ? BigDecimal.ZERO : optionalGuidePrice;
    }
    public Integer getDurationDays() {
        return durationDays == null || durationDays < 1 ? Math.max(1, itinerary == null ? 0 : itinerary.size()) : durationDays;
    }
    public Integer getDurationNights() { return durationNights == null ? Math.max(0, getDurationDays() - 1) : durationNights; }
    public List<String> getIncluded() { return included == null ? List.of() : included; }
    public List<String> getExcluded() { return excluded == null ? List.of() : excluded; }
    public PackageDetails getPackageDetails() { return packageDetails == null ? new PackageDetails() : packageDetails; }
    public ChildPolicy getChildPolicy() { return childPolicy == null ? new ChildPolicy() : childPolicy; }
    public BigDecimal getSingleRoomSupplement() {
        return singleRoomSupplement == null ? BigDecimal.ZERO : singleRoomSupplement;
    }
    public List<CancellationRule> getCancellationPolicy() {
        return cancellationPolicy == null || cancellationPolicy.isEmpty()
                ? defaultCancellationPolicy()
                : cancellationPolicy;
    }
    public String getTourGuideId() { return tourGuideId; }
    public List<ItineraryDay> getItinerary() { return itinerary; }
    public Status getStatus() { return status; }
    public BigDecimal getAvgRating() { return avgRating; }
    public Integer getReviewCount() { return reviewCount; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    private static <T> List<T> safeList(List<T> values) {
        return values == null ? new ArrayList<>() : new ArrayList<>(values);
    }

    public static List<CancellationRule> defaultCancellationPolicy() {
        return List.of(
                new CancellationRule(7, 100),
                new CancellationRule(3, 50),
                new CancellationRule(0, 0)
        );
    }


    public static class Destination {
        private String name;
        private String province;
        private Geo geo;

        protected Destination() {}
        public Destination(String name, String province, Geo geo) {
            this.name = name;
            this.province = province;
            this.geo = geo;
        }
        public String getName() { return name == null || name.isBlank() ? province : name; }
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

    public static class PackageDetails {
        private List<String> accommodation = new ArrayList<>();
        private List<String> transport = new ArrayList<>();
        private List<String> meals = new ArrayList<>();
        private List<String> tickets = new ArrayList<>();
        private List<String> insurance = new ArrayList<>();

        public PackageDetails() {}

        public PackageDetails(List<String> accommodation, List<String> transport, List<String> meals,
                              List<String> tickets, List<String> insurance) {
            this.accommodation = safeList(accommodation);
            this.transport = safeList(transport);
            this.meals = safeList(meals);
            this.tickets = safeList(tickets);
            this.insurance = safeList(insurance);
        }

        public List<String> getAccommodation() { return accommodation == null ? List.of() : accommodation; }
        public List<String> getTransport() { return transport == null ? List.of() : transport; }
        public List<String> getMeals() { return meals == null ? List.of() : meals; }
        public List<String> getTickets() { return tickets == null ? List.of() : tickets; }
        public List<String> getInsurance() { return insurance == null ? List.of() : insurance; }
    }

    public static class ChildPolicy {
        private String description = "Trẻ em tính 75% giá người lớn";
        private BigDecimal pricePercentage = BigDecimal.valueOf(75);

        public ChildPolicy() {}

        public ChildPolicy(String description, BigDecimal pricePercentage) {
            this.description = description;
            this.pricePercentage = pricePercentage;
        }

        public String getDescription() { return description; }
        public BigDecimal getPricePercentage() {
            return pricePercentage == null ? BigDecimal.valueOf(75) : pricePercentage;
        }
    }

    public static class CancellationRule {
        private int minimumDaysBeforeDeparture;
        private int refundPercentage;

        public CancellationRule() {}

        public CancellationRule(int minimumDaysBeforeDeparture, int refundPercentage) {
            this.minimumDaysBeforeDeparture = minimumDaysBeforeDeparture;
            this.refundPercentage = refundPercentage;
        }

        public int getMinimumDaysBeforeDeparture() { return minimumDaysBeforeDeparture; }
        public int getRefundPercentage() { return refundPercentage; }
    }
}
