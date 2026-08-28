package com.vietkhampha.userservice.event;

import com.vietkhampha.userservice.entity.UserProfile;
import com.vietkhampha.userservice.repository.UserProfileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class UserRegisteredListener {

    private static final Logger log = LoggerFactory.getLogger(UserRegisteredListener.class);

    private final UserProfileRepository userProfileRepository;

    public UserRegisteredListener(UserProfileRepository userProfileRepository) {
        this.userProfileRepository = userProfileRepository;
    }

    @KafkaListener(topics = "auth-events", groupId = "user-service-group")
    public void handleUserRegistered(UserRegisteredEvent event) {
        if (!"user.registered".equals(event.getEventType())) {
            return;
        }

        java.util.UUID authUserId = event.getPayload().getUserId();
        if (userProfileRepository.existsByAuthUserId(authUserId)) {
            log.info("UserProfile da ton tai cho authUserId={}, bo qua (idempotent)", authUserId);
            return;
        }

        UserProfile profile = new UserProfile(authUserId);
        userProfileRepository.save(profile);
        log.info("Da tao UserProfile moi cho authUserId={}", authUserId);
    }
}
