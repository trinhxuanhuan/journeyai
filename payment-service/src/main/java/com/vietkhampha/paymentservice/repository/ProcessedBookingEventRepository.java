package com.vietkhampha.paymentservice.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public class ProcessedBookingEventRepository {
    private final JdbcTemplate jdbcTemplate;

    public ProcessedBookingEventRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean tryClaim(UUID eventId, UUID bookingId, String eventType) {
        return jdbcTemplate.update("""
                INSERT INTO processed_booking_events (event_id, booking_id, event_type)
                VALUES (?, ?, ?)
                ON CONFLICT DO NOTHING
                """, eventId, bookingId, eventType) == 1;
    }
}
