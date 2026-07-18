package com.vietkhampha.authservice.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "otp_verifications")

public class OtpVerification {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    // Lưu HASH của mã OTP, không lưu mã gốc — cùng nguyên tắc bảo mật
    // với password_hash (ERD.md §2 ghi chú thiết kế)
    @Column(name = "otp_code_hash", nullable = false)
    private String otpCodeHash;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount = 0;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(nullable = false)
    private boolean used = false;

    protected OtpVerification() {
    }

    public OtpVerification(UUID userId, String otpCodeHash, Instant expiresAt) {
        this.userId = userId;
        this.otpCodeHash = otpCodeHash;
        this.expiresAt = expiresAt;
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public String getOtpCodeHash() { return otpCodeHash; }
    public int getAttemptCount() { return attemptCount; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getCreatedAt() { return createdAt; }

    public void incrementAttempt() {
        this.attemptCount++;
    }

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }
    public boolean isUsed() {
        return used;
    }

    public void markAsUsed() {
        this.used = true;
    }
}