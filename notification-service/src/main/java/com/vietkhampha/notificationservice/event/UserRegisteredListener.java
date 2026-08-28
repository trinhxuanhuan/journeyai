package com.vietkhampha.notificationservice.event;

import com.vietkhampha.notificationservice.entity.NotificationRecipient;
import com.vietkhampha.notificationservice.repository.NotificationRecipientRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Component
public class UserRegisteredListener {

    private static final Logger log = LoggerFactory.getLogger(UserRegisteredListener.class);

    private final NotificationRecipientRepository recipientRepository;

    public UserRegisteredListener(NotificationRecipientRepository recipientRepository) {
        this.recipientRepository = recipientRepository;
    }

    @Transactional
    @KafkaListener(
            topics = "auth-events",
            groupId = "notification-service-identity-v1",
            properties = "auto.offset.reset=earliest"
    )
    public void handle(Map<String, Object> event) {
        if (!"user.registered".equals(event.get("eventType"))) return;
        Object rawPayload = event.get("payload");
        if (!(rawPayload instanceof Map<?, ?> payload)) {
            log.warn("Bỏ qua user.registered không có payload hợp lệ");
            return;
        }

        UUID authUserId;
        try {
            authUserId = UUID.fromString(String.valueOf(payload.get("userId")));
        } catch (IllegalArgumentException exception) {
            log.warn("Bỏ qua user.registered có userId không hợp lệ");
            return;
        }

        NotificationRecipient recipient = recipientRepository.findById(authUserId)
                .orElseGet(() -> new NotificationRecipient(authUserId));
        recipient.syncIdentity(text(payload.get("email")), text(payload.get("fullName")));
        recipientRepository.save(recipient);
        log.info("Đã đồng bộ người nhận notification authUserId={}", authUserId);
    }

    private String text(Object value) {
        return value == null ? null : value.toString();
    }
}
