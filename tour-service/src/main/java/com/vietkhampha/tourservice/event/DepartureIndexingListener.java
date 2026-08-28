package com.vietkhampha.tourservice.event;

import com.vietkhampha.tourservice.document.TourSearchDocument;
import com.vietkhampha.tourservice.repository.TourSearchRepository;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;

@Component
public class DepartureIndexingListener {
    private final TourSearchRepository tourSearchRepository;

    public DepartureIndexingListener(TourSearchRepository tourSearchRepository) {
        this.tourSearchRepository = tourSearchRepository;
    }

    @SuppressWarnings("unchecked")
    @KafkaListener(topics = "booking-events", groupId = "tour-search-departure-consumer")
    public void handleBookingEvent(Map<String, Object> event) {
        String eventType = (String) event.get("eventType");
        if (!"departure.created".equals(eventType) && !"departure.updated".equals(eventType)) return;

        Map<String, Object> payload = (Map<String, Object>) event.get("payload");
        String tourId = (String) payload.get("tourId");
        tourSearchRepository.findById(tourId).ifPresent(document -> {
            java.time.Instant currentDate = LocalDate.parse(payload.get("startDate").toString())
                    .atStartOfDay().toInstant(ZoneOffset.UTC);
            boolean bookable = Boolean.TRUE.equals(payload.get("bookable"));
            document.applyDeparture(payload.get("departureId").toString(), currentDate, bookable);
            tourSearchRepository.save(document);
        });
    }
}
