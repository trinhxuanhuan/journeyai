package com.vietkhampha.authservice.dto;

import java.time.Instant;
import java.util.UUID;

public class RegisterResponse {

    private UUID userId;
    private String status;
    private Instant otpExpiresAt;

    public RegisterResponse(UUID userId, String status, Instant otpExpiresAt) {
        this.userId = userId;
        this.status = status;
        this.otpExpiresAt = otpExpiresAt;
    }

    public UUID getUserId() { return userId; }
    public String getStatus() { return status; }
    public Instant getOtpExpiresAt() { return otpExpiresAt; }
}