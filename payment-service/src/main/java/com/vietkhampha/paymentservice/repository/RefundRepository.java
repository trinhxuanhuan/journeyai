package com.vietkhampha.paymentservice.repository;

import com.vietkhampha.paymentservice.entity.Refund;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface RefundRepository extends JpaRepository<Refund, UUID> {
    boolean existsByPaymentId(UUID paymentId);
}
