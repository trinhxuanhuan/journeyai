package com.vietkhampha.tourservice.service;

import com.vietkhampha.tourservice.dto.TourRequest;
import com.vietkhampha.tourservice.dto.TourResponse;
import com.vietkhampha.tourservice.entity.Tour;
import com.vietkhampha.tourservice.event.TourEventPublisher;
import com.vietkhampha.tourservice.exception.BusinessException;
import com.vietkhampha.tourservice.exception.ErrorCode;
import com.vietkhampha.tourservice.repository.TourGuideRepository;
import com.vietkhampha.tourservice.repository.TourRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class TourService {

    private final TourRepository tourRepository;
    private final TourGuideRepository tourGuideRepository;
    private final TourEventPublisher tourEventPublisher;

    public TourService(TourRepository tourRepository, TourGuideRepository tourGuideRepository,
                       TourEventPublisher tourEventPublisher) {
        this.tourRepository = tourRepository;
        this.tourGuideRepository = tourGuideRepository;
        this.tourEventPublisher = tourEventPublisher;
    }

    public TourResponse createTour(TourRequest request) {
        validateTourGuideExists(request.getTourGuideId());

        Tour tour = new Tour(
                request.getName(),
                request.getDescription(),
                toDestination(request.getDestination()),
                request.getCoverImageUrl(),
                request.getImages(),
                request.getBasePrice(),
                request.getTourGuideId(),
                toItinerary(request.getItinerary())
        );
        Tour saved = tourRepository.save(tour);

        tourEventPublisher.publishTourCreated(saved);

        return TourResponse.from(saved);
    }

    public TourResponse updateTour(String tourId, TourRequest request) {
        validateTourGuideExists(request.getTourGuideId());

        Tour tour = findTourOrThrow(tourId);
        tour.applyUpdate(
                request.getName(),
                request.getDescription(),
                toDestination(request.getDestination()),
                request.getCoverImageUrl(),
                request.getImages(),
                request.getBasePrice(),
                request.getTourGuideId(),
                toItinerary(request.getItinerary())
        );
        Tour saved = tourRepository.save(tour);
        tourEventPublisher.publishTourUpdated(saved);

        return TourResponse.from(saved);
    }

    public void deactivateTour(String tourId) {
        Tour tour = findTourOrThrow(tourId);

        tour.markInactive();
        Tour saved = tourRepository.save(tour);
        tourEventPublisher.publishTourUpdated(saved);
    }

    public TourResponse getTourById(String tourId) {
        return TourResponse.from(findTourOrThrow(tourId));
    }

    private void validateTourGuideExists(String tourGuideId) {
        if (!tourGuideRepository.existsById(tourGuideId)) {
            throw new BusinessException(ErrorCode.TOUR_GUIDE_NOT_FOUND);
        }
    }

    private Tour findTourOrThrow(String tourId) {
        return tourRepository.findById(tourId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TOUR_NOT_FOUND));
    }

    private Tour.Destination toDestination(TourRequest.DestinationDto dto) {
        return new Tour.Destination(dto.getProvince(), new Tour.Geo(dto.getGeo().getLat(), dto.getGeo().getLng()));
    }

    private List<Tour.ItineraryDay> toItinerary(List<TourRequest.ItineraryDayDto> dtos) {
        return dtos.stream().map(day -> new Tour.ItineraryDay(
                day.getDayNumber(),
                day.getTitle(),
                day.getActivities().stream().map(act -> new Tour.Activity(
                        act.getTime(),
                        act.getDescription(),
                        act.getLocation() != null ? new Tour.Geo(act.getLocation().getLat(), act.getLocation().getLng()) : null
                )).collect(Collectors.toList())
        )).collect(Collectors.toList());
    }
}
