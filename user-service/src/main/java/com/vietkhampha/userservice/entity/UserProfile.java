package com.vietkhampha.userservice.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "user_profiles")
public class UserProfile {

    @Id
    @GeneratedValue
    private UUID id;
    @Column(name = "auth_user_id", nullable = false, unique = true)
    private UUID authUserId;

    @Column(length = 10)
    private String phone;

    @Column(name = "avatar_url", length = 2048)
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
        if (phone != null) this.phone = normalizeNullable(phone);
        if (avatarUrl != null) this.avatarUrl = normalizeNullable(avatarUrl);
        this.updatedAt = Instant.now();
    }

    public void replacePreferenceTags(List<UserPreferenceTag> newTags) {
        Map<String, UserPreferenceTag> existingByCode = new HashMap<>();
        preferenceTags.forEach(tag -> existingByCode.put(tag.getTagCode(), tag));

        Set<String> requestedCodes = new HashSet<>();
        newTags.forEach(tag -> requestedCodes.add(tag.getTagCode()));
        preferenceTags.removeIf(tag -> !requestedCodes.contains(tag.getTagCode()));

        newTags.forEach(newTag -> {
            UserPreferenceTag existingTag = existingByCode.get(newTag.getTagCode());
            if (existingTag != null) {
                existingTag.updateWeight(newTag.getWeight());
                return;
            }

            newTag.setUserProfile(this);
            preferenceTags.add(newTag);
        });
        this.updatedAt = Instant.now();
    }

    private String normalizeNullable(String value) {
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
