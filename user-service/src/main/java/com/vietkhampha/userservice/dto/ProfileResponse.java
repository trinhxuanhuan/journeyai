package com.vietkhampha.userservice.dto;

import java.util.List;
import java.util.UUID;

public class ProfileResponse {

    private UUID userId;
    private String phone;
    private String avatarUrl;
    private List<PreferenceTagDto> preferenceTags;

    public ProfileResponse(UUID userId, String phone, String avatarUrl, List<PreferenceTagDto> preferenceTags) {
        this.userId = userId;
        this.phone = phone;
        this.avatarUrl = avatarUrl;
        this.preferenceTags = List.copyOf(preferenceTags);
    }

    public UUID getUserId() { return userId; }
    public String getPhone() { return phone; }
    public String getAvatarUrl() { return avatarUrl; }
    public List<PreferenceTagDto> getPreferenceTags() { return preferenceTags; }
}
