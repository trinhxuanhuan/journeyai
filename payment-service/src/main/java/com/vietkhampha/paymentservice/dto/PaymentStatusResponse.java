package com.vietkhampha.paymentservice.dto;

import com.vietkhampha.paymentservice.entity.Payment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentStatusResponse(
        UUID paymentId,
        UUID bookingId,
        BigDecimal amount,
        String currency,
        String gateway,
        String status,
        Instant createdAt,
        Instant completedAt
) {
    public static PaymentStatusResponse from(Payment payment) {
        return new PaymentStatusResponse(
                payment.getId(), payment.getBookingId(), payment.getAmount(), payment.getCurrency(),
                payment.getGateway().name(), payment.getStatus().name(), payment.getCreatedAt(), payment.getCompletedAt()
        );
    }
}
