package com.vietkhampha.paymentservice.repository;

import com.vietkhampha.paymentservice.entity.PaymentIdempotencyKey;
import com.vietkhampha.paymentservice.entity.PaymentIdempotencyKeyId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

public interface PaymentIdempotencyKeyRepository
        extends JpaRepository<PaymentIdempotencyKey, PaymentIdempotencyKeyId> {

    @Modifying
    @Query(value = """
            INSERT INTO payment_idempotency_keys (
                customer_id,
                key,
                booking_id,
                payment_id,
                record_state,
                request_hash,
                hash_version,
                response_snapshot,
                created_at,
                replay_expires_at,
                key_expires_at
            ) VALUES (
                :customerId,
                :key,
                NULL,
                NULL,
                'PROCESSING',
                :requestHash,
                :hashVersion,
                NULL,
                :createdAt,
                :replayExpiresAt,
                :keyExpiresAt
            )
            ON CONFLICT (customer_id, key) DO NOTHING
            """, nativeQuery = true)
    int tryClaim(
            @Param("customerId") UUID customerId,
            @Param("key") String key,
            @Param("requestHash") String requestHash,
            @Param("hashVersion") String hashVersion,
            @Param("createdAt") Instant createdAt,
            @Param("replayExpiresAt") Instant replayExpiresAt,
            @Param("keyExpiresAt") Instant keyExpiresAt
    );
}
