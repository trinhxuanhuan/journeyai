package com.vietkhampha.authservice.repository;

import com.vietkhampha.authservice.entity.OtpVerification;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OtpVerificationRepository extends JpaRepository<OtpVerification, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<OtpVerification> findFirstByUserIdOrderByCreatedAtDesc(UUID userId);

    List<OtpVerification> findAllByUserIdAndUsedFalse(UUID userId);
}
