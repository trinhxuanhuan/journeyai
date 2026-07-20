package com.vietkhampha.tourservice.document;

import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.GeoPointField;
import org.springframework.data.elasticsearch.core.geo.GeoPoint;

import java.math.BigDecimal;
import java.time.Instant;

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

    @Field(type = FieldType.Date)
    private Instant nearestDepartureDate;

    @Field(type = FieldType.Boolean)
    private boolean hasAvailableSlot = true;

    protected TourSearchDocument() {}

    public TourSearchDocument(String id, String name, String description, String province, GeoPoint location,
                              BigDecimal basePrice, String coverImageUrl, BigDecimal avgRating, String status) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.province = province;
        this.location = location;
        this.basePrice = basePrice;
        this.coverImageUrl = coverImageUrl;
        this.avgRating = avgRating;
        this.status = status;
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
    public Instant getNearestDepartureDate() { return nearestDepartureDate; }
    public boolean isHasAvailableSlot() { return hasAvailableSlot; }
}