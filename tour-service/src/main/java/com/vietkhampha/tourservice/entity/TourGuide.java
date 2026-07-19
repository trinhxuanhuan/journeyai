package com.vietkhampha.tourservice.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "tour_guides")
public class TourGuide {

    @Id
    private String id;

    private String fullName;
    private String bio;
    private Integer yearsOfExperience;
    private String avatarUrl;
    private String authUserId;

    private Instant createdAt = Instant.now();

    protected TourGuide() {
    }

    public TourGuide(String fullName, String bio, Integer yearsOfExperience, String avatarUrl) {
        this.fullName = fullName;
        this.bio = bio;
        this.yearsOfExperience = yearsOfExperience;
        this.avatarUrl = avatarUrl;
    }

    public String getId() { return id; }
    public String getFullName() { return fullName; }
    public String getBio() { return bio; }
    public Integer getYearsOfExperience() { return yearsOfExperience; }
    public String getAvatarUrl() { return avatarUrl; }
    public String getAuthUserId() { return authUserId; }
    public Instant getCreatedAt() { return createdAt; }
}
