package com.vietkhampha.authservice.repository;

import com.vietkhampha.authservice.entity.OtpVerification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface OtpVerificationRepository extends JpaRepository<OtpVerification, UUID> {
    Optional<OtpVerification> findFirstByUserIdOrderByCreatedAtDesc(UUID userId);
}