package com.vietkhampha.tourservice.document;

import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.GeoPointField;
import org.springframework.data.elasticsearch.core.geo.GeoPoint;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Document(indexName = "tours")
public class TourSearchDocument {

    @Id
    private String id;

    @Field(type = FieldType.Text)
    private String name;

    @Field(type = FieldType.Text)
    private String description;

    @Field(type = FieldType.Keyword)
    private String province;

    @GeoPointField
    private GeoPoint location;

    @Field(type = FieldType.Double)
    private BigDecimal basePrice;

    @Field(type = FieldType.Keyword)
    private String coverImageUrl;

    @Field(type = FieldType.Double)
    private BigDecimal avgRating;

    @Field(type = FieldType.Keyword)
    private String status;

    @Field(type = FieldType.Keyword)
    private String tourType;

    @Field(type = FieldType.Keyword)
    private String departureLocation;

    @Field(type = FieldType.Date)
    private Instant nearestDepartureDate;

    @Field(type = FieldType.Date)
    private List<Instant> availableDepartureDates = new ArrayList<>();

    @Field(type = FieldType.Object)
    private List<DepartureAvailability> availableDepartures = new ArrayList<>();

    @Field(type = FieldType.Boolean)
    private boolean hasAvailableSlot = true;

    protected TourSearchDocument() {}

    public TourSearchDocument(String id, String name, String description, String province, GeoPoint location,
                              BigDecimal basePrice, String coverImageUrl, BigDecimal avgRating, String status,
                              String tourType, String departureLocation) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.province = province;
        this.location = location;
        this.basePrice = basePrice;
        this.coverImageUrl = coverImageUrl;
        this.avgRating = avgRating;
        this.status = status;
        this.tourType = tourType;
        this.departureLocation = departureLocation;
        this.hasAvailableSlot = "PRIVATE".equals(tourType);
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getProvince() { return province; }
    public GeoPoint getLocation() { return location; }
    public BigDecimal getBasePrice() { return basePrice; }
    public String getCoverImageUrl() { return coverImageUrl; }
    public BigDecimal getAvgRating() { return avgRating; }
    public String getStatus() { return status; }
    public String getTourType() { return tourType == null ? "GROUP" : tourType; }
    public String getDepartureLocation() { return departureLocation; }
    public Instant getNearestDepartureDate() { return nearestDepartureDate; }
    public boolean isHasAvailableSlot() { return hasAvailableSlot; }
    public List<Instant> getAvailableDepartureDates() {
        return availableDepartureDates == null ? List.of() : availableDepartureDates;
    }
    public List<DepartureAvailability> getAvailableDepartures() {
        return availableDepartures == null ? List.of() : availableDepartures;
    }

    public void applyDeparture(String departureId, Instant currentDate, boolean bookable) {
        if (availableDepartures == null) availableDepartures = new ArrayList<>();
        availableDepartures.removeIf(item -> departureId.equals(item.departureId));
        if (bookable && currentDate != null) {
            availableDepartures.add(new DepartureAvailability(departureId, currentDate));
        }
        availableDepartureDates = availableDepartures.stream()
                .map(DepartureAvailability::getStartDate)
                .distinct()
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        availableDepartureDates.sort(Comparator.naturalOrder());
        nearestDepartureDate = availableDepartureDates.stream().findFirst().orElse(null);
        hasAvailableSlot = "PRIVATE".equals(getTourType()) || !availableDepartureDates.isEmpty();
    }

    public static class DepartureAvailability {
        private String departureId;
        private Instant startDate;

        protected DepartureAvailability() {}

        public DepartureAvailability(String departureId, Instant startDate) {
            this.departureId = departureId;
            this.startDate = startDate;
        }

        public String getDepartureId() { return departureId; }
        public Instant getStartDate() { return startDate; }
    }
}
