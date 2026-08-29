package com.vietkhampha.tourservice.event;

import com.vietkhampha.tourservice.document.TourSearchDocument;
import com.vietkhampha.tourservice.entity.Tour;
import com.vietkhampha.tourservice.repository.TourRepository;
import com.vietkhampha.tourservice.repository.TourSearchRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.elasticsearch.core.geo.GeoPoint;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
@Component
public class TourIndexingListener {

    private static final Logger log = LoggerFactory.getLogger(TourIndexingListener.class);

    private final TourRepository tourRepository;
    private final TourSearchRepository tourSearchRepository;

    public TourIndexingListener(TourRepository tourRepository, TourSearchRepository tourSearchRepository) {
        this.tourRepository = tourRepository;
        this.tourSearchRepository = tourSearchRepository;
    }

    @SuppressWarnings("unchecked")
    @KafkaListener(topics = "tour-events", groupId = "tour-service-self-consumer")
    public void handleTourEvent(Map<String, Object> event) {
        String eventType = (String) event.get("eventType");
        if (!"tour.created".equals(eventType) && !"tour.updated".equals(eventType)) {
            return;
        }

        Map<String, Object> payload = (Map<String, Object>) event.get("payload");
        String tourId = (String) payload.get("tourId");

        Optional<Tour> tourOpt = tourRepository.findById(tourId);
        if (tourOpt.isEmpty()) {
            log.warn("Nhan event cho tourId={} nhung khong tim thay trong MongoDB, bo qua", tourId);
            return;
        }

        indexTour(tourOpt.get());
    }

    public void reindexAll() {
        tourRepository.findAll().forEach(this::indexTour);
    }

    private void indexTour(Tour tour) {
        String tourId = tour.getId();
        GeoPoint geoPoint = new GeoPoint(
                tour.getDestination().getGeo().getLat(),
                tour.getDestination().getGeo().getLng()
        );

        TourSearchDocument doc = new TourSearchDocument(
                tour.getId(),
                tour.getName(),
                tour.getDescription(),
                tour.getDestination().getName(),
                tour.getDestination().getProvince(),
                geoPoint,
                tour.getBasePrice(),
                tour.getCoverImageUrl(),
                tour.getAvgRating(),
                tour.getStatus().name(),
                tour.getTourType().name(),
                tour.getDepartureLocation()
        );

        tourSearchRepository.findById(tourId).ifPresent(existing ->
                existing.getAvailableDepartures().forEach(departure ->
                        doc.applyDeparture(departure.getDepartureId(), departure.getStartDate(), true)
                )
        );

        tourSearchRepository.save(doc);
        log.info("Da index tour {} vao Elasticsearch (status={})", tourId, tour.getStatus());
    }
}
