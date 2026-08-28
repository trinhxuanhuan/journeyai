package com.vietkhampha.notificationservice.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public class NotificationInboxClaimRepository {

    private final JdbcTemplate jdbcTemplate;

    public NotificationInboxClaimRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean tryClaim(UUID eventId, String sourceTopic, String eventType,
                            String aggregateId, String eventPayload) {
        return jdbcTemplate.update("""
                INSERT INTO notification_event_inbox (
                    event_id, source_topic, event_type, aggregate_id, event_payload,
                    status, attempt_count, received_at
                ) VALUES (?, ?, ?, ?, ?, 'RECEIVED', 0, CURRENT_TIMESTAMP)
                ON CONFLICT (event_id) DO NOTHING
                """, eventId, sourceTopic, eventType, aggregateId, eventPayload) == 1;
    }
}
