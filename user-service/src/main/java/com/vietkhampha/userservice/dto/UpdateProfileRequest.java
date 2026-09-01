package com.vietkhampha.userservice.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public class UpdateProfileRequest {
    @Pattern(regexp = "^$|^0\\d{9}$", message = "Số điện thoại phải gồm 10 chữ số và bắt đầu bằng 0")
    private String phone;

    @Size(max = 2048, message = "URL ảnh đại diện không được vượt quá 2048 ký tự")
    @Pattern(regexp = "^$|^https://\\S+$", message = "Ảnh đại diện phải là URL HTTPS hợp lệ")
    private String avatarUrl;

    @Valid
    @Size(max = 12, message = "Chỉ được chọn tối đa 12 sở thích")
    private List<PreferenceTagDto> preferenceTags;

    public UpdateProfileRequest() {
    }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone == null ? null : phone.trim(); }

    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl == null ? null : avatarUrl.trim(); }

    public List<PreferenceTagDto> getPreferenceTags() { return preferenceTags; }
    public void setPreferenceTags(List<PreferenceTagDto> preferenceTags) { this.preferenceTags = preferenceTags; }
}
