package com.vietkhampha.authservice.event;

import java.time.Instant;
import java.util.UUID;

// Khớp đúng shape đã chốt ở EVENT_CATALOG.md §3.1 — Topic "auth-events"
public class UserRegisteredEvent {

    private String eventType = "user.registered";
    private UUID aggregateId;
    private Instant occurredAt = Instant.now();
    private Payload payload;

    public UserRegisteredEvent(UUID userId, String email, String fullName) {
        this.aggregateId = userId;
        this.payload = new Payload(userId, email, fullName, "EMAIL");
    }

    public String getEventType() { return eventType; }
    public UUID getAggregateId() { return aggregateId; }
    public Instant getOccurredAt() { return occurredAt; }
    public Payload getPayload() { return payload; }

    public static class Payload {
        private UUID userId;
        private String email;
        private String fullName;
        private String registeredVia;

        public Payload(UUID userId, String email, String fullName, String registeredVia) {
            this.userId = userId;
            this.email = email;
            this.fullName = fullName;
            this.registeredVia = registeredVia;
        }

        public UUID getUserId() { return userId; }
        public String getEmail() { return email; }
        public String getFullName() { return fullName; }
        public String getRegisteredVia() { return registeredVia; }
    }
}