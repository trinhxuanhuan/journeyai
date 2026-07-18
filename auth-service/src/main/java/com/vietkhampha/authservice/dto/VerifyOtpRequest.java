package com.vietkhampha.authservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public class VerifyOtpRequest {

    @NotNull(message = "userId không được để trống")
    private UUID userId;

    @NotBlank(message = "otpCode không được để trống")
    private String otpCode;

    protected VerifyOtpRequest() {
    }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public String getOtpCode() { return otpCode; }
    public void setOtpCode(String otpCode) { this.otpCode = otpCode; }
}