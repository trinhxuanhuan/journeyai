package com.vietkhampha.notificationservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vietkhampha.notificationservice.repository.NotificationInboxClaimRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

@Service
public class NotificationInboxService {

    private final NotificationInboxClaimRepository claimRepository;
    private final ObjectMapper objectMapper;

    public NotificationInboxService(NotificationInboxClaimRepository claimRepository, ObjectMapper objectMapper) {
        this.claimRepository = claimRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public UUID accept(String sourceTopic, Map<String, Object> event) {
        String eventType = requiredText(event, "eventType");
        String aggregateId = optionalText(event.get("aggregateId"));
        String serialized = serialize(event);
        UUID eventId = eventId(event, sourceTopic, eventType, aggregateId, serialized);
        claimRepository.tryClaim(eventId, sourceTopic, eventType, aggregateId, serialized);
        return eventId;
    }

    private UUID eventId(Map<String, Object> event, String sourceTopic, String eventType,
                         String aggregateId, String serialized) {
        Object rawEventId = event.get("eventId");
        if (rawEventId != null) {
            try {
                return UUID.fromString(rawEventId.toString());
            } catch (IllegalArgumentException ignored) {
                // Event legacy có eventId sai định dạng vẫn được định danh ổn định bằng fingerprint.
            }
        }
        String fingerprint = sourceTopic + "|" + eventType + "|" + aggregateId + "|" + serialized;
        return UUID.nameUUIDFromBytes(fingerprint.getBytes(StandardCharsets.UTF_8));
    }

    private String serialize(Map<String, Object> event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Không thể lưu event thông báo", exception);
        }
    }

    private String requiredText(Map<String, Object> event, String field) {
        String value = optionalText(event.get(field));
        if (value == null) throw new IllegalArgumentException("Event thiếu trường " + field);
        return value;
    }

    private String optionalText(Object value) {
        if (value == null || value.toString().isBlank()) return null;
        return value.toString();
    }
}
