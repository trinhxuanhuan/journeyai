package com.vietkhampha.paymentservice.repository;

import com.vietkhampha.paymentservice.entity.PaymentLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PaymentLogRepository extends JpaRepository<PaymentLog, UUID> {
}