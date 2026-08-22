package com.vietkhampha.bookingservice.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public class ProcessedPaymentEventRepository {

    private final JdbcTemplate jdbcTemplate;

    public ProcessedPaymentEventRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean tryClaim(UUID eventId, UUID paymentId, UUID bookingId, String eventType) {
        int inserted = jdbcTemplate.update("""
                INSERT INTO processed_payment_events (event_id, payment_id, booking_id, event_type)
                VALUES (?, ?, ?, ?)
                ON CONFLICT DO NOTHING
                """, eventId, paymentId, bookingId, eventType);
        return inserted == 1;
    }

    public boolean matchesExistingClaim(UUID eventId, UUID paymentId, UUID bookingId, String eventType) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject("""
                SELECT
                    EXISTS (
                        SELECT 1
                        FROM processed_payment_events
                        WHERE (event_id = ? OR payment_id = ?)
                          AND payment_id = ?
                          AND booking_id = ?
                          AND event_type = ?
                    )
                    AND NOT EXISTS (
                        SELECT 1
                        FROM processed_payment_events
                        WHERE event_id = ?
                          AND (
                              payment_id <> ?
                              OR booking_id <> ?
                              OR event_type <> ?
                          )
                    )
                """, Boolean.class,
                eventId, paymentId, paymentId, bookingId, eventType,
                eventId, paymentId, bookingId, eventType));
    }
}
