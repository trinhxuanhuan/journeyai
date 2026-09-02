package com.vietkhampha.authservice.dto;

import java.time.Instant;

public class ResendOtpResponse {

    private final Instant otpExpiresAt;
    private final Instant otpResendAvailableAt;

    public ResendOtpResponse(Instant otpExpiresAt, Instant otpResendAvailableAt) {
        this.otpExpiresAt = otpExpiresAt;
        this.otpResendAvailableAt = otpResendAvailableAt;
    }

    public Instant getOtpExpiresAt() {
        return otpExpiresAt;
    }

    public Instant getOtpResendAvailableAt() {
        return otpResendAvailableAt;
    }
}
