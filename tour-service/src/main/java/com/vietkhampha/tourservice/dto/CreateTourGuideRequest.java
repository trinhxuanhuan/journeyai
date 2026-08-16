package com.vietkhampha.tourservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;

public class CreateTourGuideRequest {

    @NotBlank(message = "Ten huong dan vien khong duoc de trong")
    private String fullName;

    private String bio;

    @PositiveOrZero(message = "So nam kinh nghiem khong duoc am")
    private Integer yearsOfExperience;

    private String avatarUrl;

    protected CreateTourGuideRequest() {
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getBio() {
        return bio;
    }

    public void setBio(String bio) {
        this.bio = bio;
    }

    public Integer getYearsOfExperience() {
        return yearsOfExperience;
    }

    public void setYearsOfExperience(Integer yearsOfExperience) {
        this.yearsOfExperience = yearsOfExperience;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }
}
