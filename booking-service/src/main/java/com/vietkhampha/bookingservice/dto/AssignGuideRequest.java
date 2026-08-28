package com.vietkhampha.bookingservice.dto;

import jakarta.validation.constraints.NotBlank;

public class AssignGuideRequest {
    @NotBlank(message = "guideId khong duoc de trong")
    private String guideId;

    public String getGuideId() { return guideId; }
    public void setGuideId(String guideId) { this.guideId = guideId; }
}
