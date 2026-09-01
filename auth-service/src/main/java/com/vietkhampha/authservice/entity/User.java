package com.vietkhampha.authservice.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false, unique = true)
    private String email;

    // Nullable — user đăng nhập thuần Google (UC-A02) không cần password nội bộ
    // (đúng ghi chú thiết kế ở ERD.md §2)
    @Column(name = "password_hash")
    private String passwordHash;

    @Column(name = "full_name")
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role = Role.CUSTOMER;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.UNVERIFIED;

    @Column(name = "locked_reason")
    private String lockedReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public enum Role {
        CUSTOMER, ADMIN
    }

    public enum Status {
        UNVERIFIED, ACTIVE, LOCKED
    }

    // JPA bắt buộc phải có constructor rỗng
    protected User() {
    }

    public User(String email, String passwordHash, String fullName) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.fullName = fullName;
    }

    // Getters — JPA/Jackson cần để đọc/ghi dữ liệu
    public UUID getId() { return id; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public String getFullName() { return fullName; }
    public Role getRole() { return role; }
    public Status getStatus() { return status; }
    public String getLockedReason() { return lockedReason; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setStatus(Status status) {
        this.status = status;
        this.updatedAt = Instant.now();
    }

    public void updateFullName(String fullName) {
        this.fullName = fullName;
        this.updatedAt = Instant.now();
    }
}
