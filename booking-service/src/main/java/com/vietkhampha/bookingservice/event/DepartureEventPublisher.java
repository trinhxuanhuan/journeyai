package com.vietkhampha.bookingservice.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vietkhampha.bookingservice.entity.OutboxEvent;
import com.vietkhampha.bookingservice.entity.TourSlot;
import com.vietkhampha.bookingservice.repository.OutboxEventRepository;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class DepartureEventPublisher {
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    public DepartureEventPublisher(OutboxEventRepository outboxEventRepository, ObjectMapper objectMapper) {
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
    }

    public void publishCreated(TourSlot departure) {
        publish("departure.created", departure);
    }

    public void publishUpdated(TourSlot departure) {
        publish("departure.updated", departure);
    }

    private void publish(String eventType, TourSlot departure) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("departureId", departure.getId().toString());
            payload.put("tourId", departure.getTourId());
            payload.put("startDate", departure.getStartDate().toString());
            payload.put("endDate", departure.getEndDate().toString());
            payload.put("availableSeats", departure.getAvailableSlots());
            payload.put("status", departure.getEffectiveStatus());
            payload.put("bookable", departure.getStatus() == TourSlot.Status.OPEN
                    && departure.getAvailableSlots() > 0);
            OutboxEvent event = new OutboxEvent(
                    "DEPARTURE",
                    departure.getId(),
                    eventType,
                    objectMapper.writeValueAsString(payload)
            );
            outboxEventRepository.save(event);
        } catch (Exception exception) {
            throw new IllegalStateException("Khong the tao Departure outbox event", exception);
        }
    }
}
