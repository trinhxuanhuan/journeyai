package com.vietkhampha.userservice.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;

import java.util.List;

public class UpdateProfileRequest {
    @Pattern(regexp = "^0\\d{9}$", message = "So dien thoai khong dung dinh dang")
    private String phone;

    private String avatarUrl;

    @Valid
    private List<PreferenceTagDto> preferenceTags;

    protected UpdateProfileRequest() {
    }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }

    public List<PreferenceTagDto> getPreferenceTags() { return preferenceTags; }
    public void setPreferenceTags(List<PreferenceTagDto> preferenceTags) { this.preferenceTags = preferenceTags; }
}
