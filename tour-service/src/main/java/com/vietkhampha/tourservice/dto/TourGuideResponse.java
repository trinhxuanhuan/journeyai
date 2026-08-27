package com.vietkhampha.tourservice.dto;

import com.vietkhampha.tourservice.entity.TourGuide;

import java.time.Instant;

public record TourGuideResponse(
        String id,
        String fullName,
        String bio,
        Integer yearsOfExperience,
        String avatarUrl,
        boolean active,
        Instant createdAt
) {
    public static TourGuideResponse from(TourGuide guide) {
        return new TourGuideResponse(
                guide.getId(),
                guide.getFullName(),
                guide.getBio(),
                guide.getYearsOfExperience(),
                guide.getAvatarUrl(),
                guide.isActive(),
                guide.getCreatedAt()
        );
    }
}
