package com.vietkhampha.authservice.dto;

import java.time.Instant;
import java.util.UUID;

public class RegisterResponse {

    private UUID userId;
    private String status;
    private Instant otpExpiresAt;
    private Instant otpResendAvailableAt;

    public RegisterResponse(
            UUID userId,
            String status,
            Instant otpExpiresAt,
            Instant otpResendAvailableAt
    ) {
        this.userId = userId;
        this.status = status;
        this.otpExpiresAt = otpExpiresAt;
        this.otpResendAvailableAt = otpResendAvailableAt;
    }

    public UUID getUserId() { return userId; }
    public String getStatus() { return status; }
    public Instant getOtpExpiresAt() { return otpExpiresAt; }
    public Instant getOtpResendAvailableAt() { return otpResendAvailableAt; }
}
