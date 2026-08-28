package com.vietkhampha.userservice.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "user_profiles")
public class UserProfile {

    @Id
    @GeneratedValue
    private UUID id;
    @Column(name = "auth_user_id", nullable = false, unique = true)
    private UUID authUserId;

    private String phone;

    @Column(name = "avatar_url")
    private String avatarUrl;

    @OneToMany(mappedBy = "userProfile", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<UserPreferenceTag> preferenceTags = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected UserProfile() {
    }

    public UserProfile(UUID authUserId) {
        this.authUserId = authUserId;
    }

    public UUID getId() { return id; }
    public UUID getAuthUserId() { return authUserId; }
    public String getPhone() { return phone; }
    public String getAvatarUrl() { return avatarUrl; }
    public List<UserPreferenceTag> getPreferenceTags() { return preferenceTags; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void updateProfile(String phone, String avatarUrl) {
        if (phone != null) this.phone = phone;
        if (avatarUrl != null) this.avatarUrl = avatarUrl;
        this.updatedAt = Instant.now();
    }

    public void replacePreferenceTags(List<UserPreferenceTag> newTags) {
        this.preferenceTags.clear();
        newTags.forEach(tag -> tag.setUserProfile(this));
        this.preferenceTags.addAll(newTags);
        this.updatedAt = Instant.now();
    }
}
