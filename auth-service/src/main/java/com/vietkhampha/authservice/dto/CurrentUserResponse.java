package com.vietkhampha.authservice.dto;

import com.vietkhampha.authservice.entity.User;

import java.time.Instant;
import java.util.UUID;

public class CurrentUserResponse {

    private final UUID userId;
    private final String email;
    private final String fullName;
    private final String role;
    private final String status;
    private final Instant createdAt;
    private final Instant updatedAt;

    public CurrentUserResponse(
            UUID userId,
            String email,
            String fullName,
            String role,
            String status,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.userId = userId;
        this.email = email;
        this.fullName = fullName;
        this.role = role;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static CurrentUserResponse from(User user) {
        return new CurrentUserResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getRole().name(),
                user.getStatus().name(),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }

    public UUID getUserId() { return userId; }
    public String getEmail() { return email; }
    public String getFullName() { return fullName; }
    public String getRole() { return role; }
    public String getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
