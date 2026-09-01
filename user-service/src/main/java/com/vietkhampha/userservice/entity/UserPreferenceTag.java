package com.vietkhampha.userservice.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "user_preference_tags",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_user_preference_tags_profile_code",
                columnNames = {"user_profile_id", "tag_code"}
        )
)
public class UserPreferenceTag {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "user_profile_id", nullable = false)
    private UserProfile userProfile;
    @Column(name = "tag_code", nullable = false, length = 50)
    private String tagCode;

    @Column(nullable = false, precision = 4, scale = 3)
    private BigDecimal weight = BigDecimal.ONE;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected UserPreferenceTag() {
    }

    public UserPreferenceTag(String tagCode, BigDecimal weight) {
        this.tagCode = tagCode;
        this.weight = weight;
    }

    public UUID getId() { return id; }
    public UserProfile getUserProfile() { return userProfile; }
    public void setUserProfile(UserProfile userProfile) { this.userProfile = userProfile; }
    public String getTagCode() { return tagCode; }
    public BigDecimal getWeight() { return weight; }
}
