package com.vietkhampha.userservice.event;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class UserEventPublisher {
    private static final String TOPIC = "user-events";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public UserEventPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publishPreferencesUpdated(UUID userId, List<Map<String, Object>> preferenceTags) {
        Map<String, Object> event = Map.of(
                "eventType", "user.preferences_updated",
                "aggregateId", userId,
                "occurredAt", Instant.now().toString(),
                "payload", Map.of("userId", userId, "preferenceTags", preferenceTags)
        );
        kafkaTemplate.send(TOPIC, userId.toString(), event);
    }
}