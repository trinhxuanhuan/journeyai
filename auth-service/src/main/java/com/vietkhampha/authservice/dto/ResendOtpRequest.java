package com.vietkhampha.authservice.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public class ResendOtpRequest {

    @NotNull(message = "userId không được để trống")
    private UUID userId;

    public ResendOtpRequest() {
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }
}
