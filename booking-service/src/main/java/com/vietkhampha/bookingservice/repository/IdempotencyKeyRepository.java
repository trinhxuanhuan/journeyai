package com.vietkhampha.bookingservice.repository;

import com.vietkhampha.bookingservice.entity.IdempotencyKey;
import com.vietkhampha.bookingservice.entity.IdempotencyKeyId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, IdempotencyKeyId> {

    @Modifying
    @Query(value = """
            INSERT INTO idempotency_keys (
                customer_id,
                key,
                booking_id,
                record_state,
                request_hash,
                hash_version,
                response_snapshot,
                created_at,
                expires_at
            ) VALUES (
                :customerId,
                :key,
                NULL,
                'PROCESSING',
                :requestHash,
                :hashVersion,
                NULL,
                :createdAt,
                :expiresAt
            )
            ON CONFLICT (customer_id, key) DO NOTHING
            """, nativeQuery = true)
    int tryClaim(
            @Param("customerId") UUID customerId,
            @Param("key") String key,
            @Param("requestHash") String requestHash,
            @Param("hashVersion") String hashVersion,
            @Param("createdAt") Instant createdAt,
            @Param("expiresAt") Instant expiresAt
    );
}
