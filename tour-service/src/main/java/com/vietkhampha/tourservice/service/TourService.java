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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
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
        validateLegacyTourGuideIfPresent(request.getTourGuideId());
        TourConfiguration config = resolveConfiguration(request);

        Tour tour = new Tour(
                request.getName(),
                request.getDescription(),
                toDestination(request.getDestination()),
                request.getCoverImageUrl(),
                request.getImages(),
                request.getBasePrice(),
                config.tourType(),
                config.priceModel(),
                config.departureLocation(),
                config.meetingPoint(),
                request.getMeetingTime(),
                config.minGroupSize(),
                config.maxGroupSize(),
                config.guideMode(),
                config.optionalGuidePrice(),
                config.durationDays(),
                config.durationNights(),
                request.getIncluded(),
                request.getExcluded(),
                toPackageDetails(request.getPackageDetails()),
                toChildPolicy(request.getChildPolicy()),
                defaultMoney(request.getSingleRoomSupplement()),
                toCancellationPolicy(request.getCancellationPolicy()),
                request.getTourGuideId(),
                toItinerary(request.getItinerary())
        );
        Tour saved = tourRepository.save(tour);

        tourEventPublisher.publishTourCreated(saved);

        return TourResponse.from(saved);
    }

    public TourResponse updateTour(String tourId, TourRequest request) {
        validateLegacyTourGuideIfPresent(request.getTourGuideId());
        TourConfiguration config = resolveConfiguration(request);

        Tour tour = findTourOrThrow(tourId);
        tour.applyUpdate(
                request.getName(),
                request.getDescription(),
                toDestination(request.getDestination()),
                request.getCoverImageUrl(),
                request.getImages(),
                request.getBasePrice(),
                config.tourType(),
                config.priceModel(),
                config.departureLocation(),
                config.meetingPoint(),
                request.getMeetingTime(),
                config.minGroupSize(),
                config.maxGroupSize(),
                config.guideMode(),
                config.optionalGuidePrice(),
                config.durationDays(),
                config.durationNights(),
                request.getIncluded(),
                request.getExcluded(),
                toPackageDetails(request.getPackageDetails()),
                toChildPolicy(request.getChildPolicy()),
                defaultMoney(request.getSingleRoomSupplement()),
                toCancellationPolicy(request.getCancellationPolicy()),
                request.getTourGuideId(),
                toItinerary(request.getItinerary())
        );
        Tour saved = tourRepository.save(tour);
        tourEventPublisher.publishTourUpdated(saved);

        return TourResponse.from(saved);
    }
    public TourResponse getPublicTourById(String tourId) {
        Tour tour = findTourOrThrow(tourId);
        if (tour.getStatus() == Tour.Status.INACTIVE) {
            throw new BusinessException(ErrorCode.TOUR_NOT_FOUND);
        }
        return TourResponse.from(tour);
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

    public List<TourResponse> listToursForAdmin() {
        return tourRepository.findAll().stream().map(TourResponse::from).toList();
    }

    private void validateLegacyTourGuideIfPresent(String tourGuideId) {
        if (tourGuideId != null && !tourGuideId.isBlank() && !tourGuideRepository.existsById(tourGuideId)) {
            throw new BusinessException(ErrorCode.TOUR_GUIDE_NOT_FOUND);
        }
    }

    private TourConfiguration resolveConfiguration(TourRequest request) {
        Tour.TourType tourType = request.getTourType() == null ? Tour.TourType.GROUP : request.getTourType();
        Tour.PriceModel priceModel = request.getPriceModel() == null
                ? Tour.PriceModel.PER_PERSON
                : request.getPriceModel();
        Tour.GuideMode guideMode = request.getGuideMode() == null
                ? (tourType == Tour.TourType.GROUP ? Tour.GuideMode.INCLUDED : Tour.GuideMode.NONE)
                : request.getGuideMode();

        if (tourType == Tour.TourType.GROUP
                && (priceModel != Tour.PriceModel.PER_PERSON || guideMode != Tour.GuideMode.INCLUDED)) {
            throw new BusinessException(ErrorCode.TOUR_CONFIGURATION_INVALID);
        }

        int minGroupSize = request.getMinGroupSize() == null ? 1 : request.getMinGroupSize();
        int maxGroupSize = request.getMaxGroupSize() == null
                ? (tourType == Tour.TourType.GROUP ? 30 : 20)
                : request.getMaxGroupSize();
        if (minGroupSize > maxGroupSize) {
            throw new BusinessException(ErrorCode.TOUR_CONFIGURATION_INVALID);
        }

        int itineraryDays = request.getItinerary() == null ? 1 : Math.max(1, request.getItinerary().size());
        int durationDays = request.getDurationDays() == null ? itineraryDays : request.getDurationDays();
        int durationNights = request.getDurationNights() == null
                ? Math.max(0, durationDays - 1)
                : request.getDurationNights();
        if (durationNights > Math.max(0, durationDays - 1)) {
            throw new BusinessException(ErrorCode.TOUR_CONFIGURATION_INVALID);
        }

        String departureLocation = normalizeOrDefault(
                request.getDepartureLocation(),
                request.getDestination().getProvince()
        );
        String meetingPoint = normalizeOrDefault(request.getMeetingPoint(), departureLocation);

        return new TourConfiguration(
                tourType,
                priceModel,
                departureLocation,
                meetingPoint,
                minGroupSize,
                maxGroupSize,
                guideMode,
                defaultMoney(request.getOptionalGuidePrice()),
                durationDays,
                durationNights
        );
    }

    private Tour.PackageDetails toPackageDetails(TourRequest.PackageDetailsDto dto) {
        if (dto == null) return new Tour.PackageDetails();
        return new Tour.PackageDetails(
                dto.getAccommodation(), dto.getTransport(), dto.getMeals(), dto.getTickets(), dto.getInsurance()
        );
    }

    private Tour.ChildPolicy toChildPolicy(TourRequest.ChildPolicyDto dto) {
        if (dto == null) return new Tour.ChildPolicy();
        String description = normalizeOrDefault(dto.getDescription(), "Tre em tinh theo ty le gia nguoi lon");
        BigDecimal percentage = dto.getPricePercentage() == null ? BigDecimal.valueOf(75) : dto.getPricePercentage();
        return new Tour.ChildPolicy(description, percentage);
    }

    private List<Tour.CancellationRule> toCancellationPolicy(List<TourRequest.CancellationRuleDto> rules) {
        if (rules == null || rules.isEmpty()) return Tour.defaultCancellationPolicy();
        List<Tour.CancellationRule> mapped = new ArrayList<>(rules.stream()
                .map(rule -> new Tour.CancellationRule(
                        rule.getMinimumDaysBeforeDeparture(), rule.getRefundPercentage()
                ))
                .toList());
        mapped.sort(Comparator.comparingInt(Tour.CancellationRule::getMinimumDaysBeforeDeparture).reversed());
        return mapped;
    }

    private BigDecimal defaultMoney(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private String normalizeOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private Tour findTourOrThrow(String tourId) {
        return tourRepository.findById(tourId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TOUR_NOT_FOUND));
    }

    private Tour.Destination toDestination(TourRequest.DestinationDto dto) {
        return new Tour.Destination(
                normalizeOrDefault(dto.getName(), dto.getProvince()),
                dto.getProvince().trim(),
                new Tour.Geo(dto.getGeo().getLat(), dto.getGeo().getLng())
        );
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

    private record TourConfiguration(
            Tour.TourType tourType,
            Tour.PriceModel priceModel,
            String departureLocation,
            String meetingPoint,
            int minGroupSize,
            int maxGroupSize,
            Tour.GuideMode guideMode,
            BigDecimal optionalGuidePrice,
            int durationDays,
            int durationNights
    ) {}
}
