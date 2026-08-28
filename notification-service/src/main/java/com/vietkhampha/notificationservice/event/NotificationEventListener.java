package com.vietkhampha.notificationservice.event;

import com.vietkhampha.notificationservice.service.NotificationEventProcessor;
import com.vietkhampha.notificationservice.service.NotificationInboxService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Component
public class NotificationEventListener {

    private final NotificationInboxService inboxService;
    private final NotificationEventProcessor eventProcessor;

    public NotificationEventListener(NotificationInboxService inboxService,
                                     NotificationEventProcessor eventProcessor) {
        this.inboxService = inboxService;
        this.eventProcessor = eventProcessor;
    }

    @KafkaListener(topics = "booking-events", groupId = "notification-service-booking-v1")
    public void handleBookingEvent(Map<String, Object> event) {
        receive("booking-events", event);
    }

    @KafkaListener(topics = "payment-events", groupId = "notification-service-payment-v1")
    public void handlePaymentEvent(Map<String, Object> event) {
        receive("payment-events", event);
    }

    private void receive(String topic, Map<String, Object> event) {
        UUID eventId = inboxService.accept(topic, event);
        eventProcessor.process(eventId);
    }
}
