package com.vietkhampha.authservice.exception;

import java.time.Instant;
import java.util.UUID;

public class AccountVerificationRequiredException extends BusinessException {

    private final UUID userId;
    private final String email;
    private final Instant otpExpiresAt;
    private final Instant otpResendAvailableAt;

    public AccountVerificationRequiredException(
            UUID userId,
            String email,
            Instant otpExpiresAt,
            Instant otpResendAvailableAt
    ) {
        super(ErrorCode.ACCOUNT_UNVERIFIED);
        this.userId = userId;
        this.email = email;
        this.otpExpiresAt = otpExpiresAt;
        this.otpResendAvailableAt = otpResendAvailableAt;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getEmail() {
        return email;
    }

    public Instant getOtpExpiresAt() {
        return otpExpiresAt;
    }

    public Instant getOtpResendAvailableAt() {
        return otpResendAvailableAt;
    }
}
