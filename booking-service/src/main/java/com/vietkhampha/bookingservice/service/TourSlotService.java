package com.vietkhampha.bookingservice.service;

import com.vietkhampha.bookingservice.client.TourServiceClient;
import com.vietkhampha.bookingservice.dto.CreateSlotRequest;
import com.vietkhampha.bookingservice.dto.CreateDepartureRequest;
import com.vietkhampha.bookingservice.dto.DepartureResponse;
import com.vietkhampha.bookingservice.dto.PublicTourSlotResponse;
import com.vietkhampha.bookingservice.dto.UpdateDepartureRequest;
import com.vietkhampha.bookingservice.entity.TourSlot;
import com.vietkhampha.bookingservice.event.DepartureEventPublisher;
import com.vietkhampha.bookingservice.exception.BusinessException;
import com.vietkhampha.bookingservice.exception.ErrorCode;
import com.vietkhampha.bookingservice.repository.TourSlotRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@Service
public class TourSlotService {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final TourSlotRepository tourSlotRepository;
    private final TourServiceClient tourServiceClient;
    private final DepartureEventPublisher departureEventPublisher;

    public TourSlotService(TourSlotRepository tourSlotRepository, TourServiceClient tourServiceClient,
                           DepartureEventPublisher departureEventPublisher) {
        this.tourSlotRepository = tourSlotRepository;
        this.tourServiceClient = tourServiceClient;
        this.departureEventPublisher = departureEventPublisher;
    }

    @Transactional
    public TourSlot createSlot(CreateSlotRequest request) {
        TourServiceClient.TourInfo tour = tourServiceClient.requireActiveTour(request.getTourId());

        tourSlotRepository.findByTourIdAndDepartureDate(request.getTourId(), request.getDepartureDate())
                .ifPresent(existing -> {
                    throw new BusinessException(ErrorCode.SLOT_ALREADY_EXISTS);
                });

        LocalDate endDate = request.getDepartureDate().plusDays(Math.max(0, tour.durationDays() - 1L));
        String legacyGuideId = tour.legacyGuideId();
        TourSlot slot = new TourSlot(
                request.getTourId(), request.getDepartureDate(), endDate, request.getMaxCapacity(),
                legacyGuideId, null,
                legacyGuideId == null || legacyGuideId.isBlank() ? TourSlot.Status.CLOSED : TourSlot.Status.OPEN
        );
        try {
            TourSlot saved = tourSlotRepository.saveAndFlush(slot);
            departureEventPublisher.publishCreated(saved);
            return saved;
        } catch (DataIntegrityViolationException exception) {
            if (hasSqlState(exception, "23505")) {
                throw new BusinessException(ErrorCode.SLOT_ALREADY_EXISTS);
            }
            throw exception;
        }
    }

    @Transactional
    public DepartureResponse createDeparture(String tourId, CreateDepartureRequest request) {
        TourServiceClient.TourInfo tour = tourServiceClient.requireActiveTour(tourId);
        if (!"GROUP".equals(tour.tourType())) {
            throw new BusinessException(ErrorCode.DEPARTURE_CONFIGURATION_INVALID);
        }
        if (request.getStartDate().isBefore(LocalDate.now(BUSINESS_ZONE))
                || request.getEndDate().isBefore(request.getStartDate())) {
            throw new BusinessException(ErrorCode.DEPARTURE_CONFIGURATION_INVALID);
        }
        tourServiceClient.requireActiveGuide(request.getGuideId());
        ensureUnique(tourId, request.getStartDate());

        TourSlot departure = new TourSlot(
                tourId,
                request.getStartDate(),
                request.getEndDate(),
                request.getCapacity(),
                request.getGuideId(),
                request.getPriceOverride(),
                TourSlot.Status.OPEN
        );
        try {
            TourSlot saved = tourSlotRepository.saveAndFlush(departure);
            departureEventPublisher.publishCreated(saved);
            return DepartureResponse.from(saved);
        } catch (DataIntegrityViolationException exception) {
            if (hasSqlState(exception, "23505")) {
                throw new BusinessException(ErrorCode.SLOT_ALREADY_EXISTS);
            }
            throw exception;
        }
    }

    @Transactional
    public DepartureResponse updateDeparture(UUID departureId, UpdateDepartureRequest request) {
        TourSlot departure = tourSlotRepository.findByIdForUpdate(departureId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SLOT_NOT_FOUND));
        if (departure.getStatus() == TourSlot.Status.CANCELLED
                || departure.getStatus() == TourSlot.Status.COMPLETED) {
            throw new BusinessException(ErrorCode.DEPARTURE_CONFIGURATION_INVALID);
        }
        if (request.getStatus() == TourSlot.Status.CANCELLED
                || request.getStatus() == TourSlot.Status.COMPLETED) {
            throw new BusinessException(ErrorCode.DEPARTURE_CONFIGURATION_INVALID);
        }
        if (request.getGuideId() != null && !request.getGuideId().isBlank()) {
            tourServiceClient.requireActiveGuide(request.getGuideId());
        }
        if (request.getStartDate() != null
                && !request.getStartDate().equals(departure.getStartDate())) {
            ensureUnique(departure.getTourId(), request.getStartDate());
        }
        LocalDate resolvedStartDate = request.getStartDate() == null
                ? departure.getStartDate() : request.getStartDate();
        TourSlot.Status resolvedStatus = request.getStatus() == null
                ? departure.getStatus() : request.getStatus();
        if (resolvedStatus == TourSlot.Status.OPEN
                && resolvedStartDate.isBefore(LocalDate.now(BUSINESS_ZONE))) {
            throw new BusinessException(ErrorCode.DEPARTURE_CONFIGURATION_INVALID);
        }
        try {
            departure.applyUpdate(
                    request.getStartDate(), request.getEndDate(), request.getCapacity(), request.getGuideId(),
                    request.getPriceOverride(), request.getStatus()
            );
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.DEPARTURE_CONFIGURATION_INVALID);
        }
        TourSlot saved = tourSlotRepository.save(departure);
        departureEventPublisher.publishUpdated(saved);
        return DepartureResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<DepartureResponse> getAdminDepartures(String tourId) {
        return tourSlotRepository.findByTourIdOrderByDepartureDateAsc(tourId)
                .stream().map(DepartureResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<DepartureResponse> getPublicDepartures(String tourId) {
        return tourSlotRepository
                .findByTourIdAndStatusAndDepartureDateGreaterThanEqualOrderByDepartureDateAsc(
                        tourId, TourSlot.Status.OPEN, LocalDate.now(BUSINESS_ZONE)
                )
                .stream().map(DepartureResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<PublicTourSlotResponse> getPublicSlots(String tourId) {
        return tourSlotRepository
                .findByTourIdAndStatusAndDepartureDateAfterOrderByDepartureDateAsc(
                        tourId,
                        TourSlot.Status.OPEN,
                        LocalDate.now(BUSINESS_ZONE)
                )
                .stream()
                .map(PublicTourSlotResponse::from)
                .toList();
    }

    private void ensureUnique(String tourId, LocalDate startDate) {
        tourSlotRepository.findByTourIdAndDepartureDate(tourId, startDate)
                .ifPresent(existing -> {
                    throw new BusinessException(ErrorCode.SLOT_ALREADY_EXISTS);
                });
    }

    private boolean hasSqlState(Throwable throwable, String expectedSqlState) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof SQLException sqlException
                    && expectedSqlState.equals(sqlException.getSQLState())) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
