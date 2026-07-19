package com.vietkhampha.userservice.event;
public class UserRegisteredEvent {

    private String eventType;
    private Payload payload;

    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }

    public Payload getPayload() { return payload; }
    public void setPayload(Payload payload) { this.payload = payload; }

    public static class Payload {
        private java.util.UUID userId;
        private String email;
        private String fullName;

        public java.util.UUID getUserId() { return userId; }
        public void setUserId(java.util.UUID userId) { this.userId = userId; }

        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }

        public String getFullName() { return fullName; }
        public void setFullName(String fullName) { this.fullName = fullName; }
    }
}