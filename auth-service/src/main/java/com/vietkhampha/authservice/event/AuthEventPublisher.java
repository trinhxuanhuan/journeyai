package com.vietkhampha.authservice.event;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class AuthEventPublisher {

    // Tên topic khớp đúng EVENT_CATALOG.md §3 ("Topic: auth-events")
    private static final String TOPIC = "auth-events";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public AuthEventPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publishUserRegistered(java.util.UUID userId, String email, String fullName) {
        UserRegisteredEvent event = new UserRegisteredEvent(userId, email, fullName);
        // Key = userId — đảm bảo mọi event của cùng 1 user vào cùng 1 partition,
        // giữ đúng thứ tự xử lý nếu sau này có nhiều event khác của user đó.
        kafkaTemplate.send(TOPIC, userId.toString(), event);
    }
}