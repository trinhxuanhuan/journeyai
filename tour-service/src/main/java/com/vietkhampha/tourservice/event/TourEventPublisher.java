package com.vietkhampha.tourservice.event;

import com.vietkhampha.tourservice.entity.Tour;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Component
public class TourEventPublisher {
    private static final String TOPIC = "tour-events";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public TourEventPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publishTourCreated(Tour tour) {
        publish("tour.created", tour);
    }

    public void publishTourUpdated(Tour tour) {
        publish("tour.updated", tour);
    }

    private void publish(String eventType, Tour tour) {
        Map<String, Object> event = Map.of(
                "eventType", eventType,
                "aggregateId", tour.getId(),
                "occurredAt", Instant.now().toString(),
                "payload", Map.of(
                        "tourId", tour.getId(),
                        "name", tour.getName(),
                        "description", tour.getDescription(),
                        "destination", tour.getDestination().getProvince(),
                        "tags", List.of(),
                        "status", tour.getStatus().name()
                )
        );
        kafkaTemplate.send(TOPIC, tour.getId(), event);
    }
}