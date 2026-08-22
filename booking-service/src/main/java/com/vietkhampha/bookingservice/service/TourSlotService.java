package com.vietkhampha.bookingservice.service;

import com.vietkhampha.bookingservice.client.TourServiceClient;
import com.vietkhampha.bookingservice.dto.CreateSlotRequest;
import com.vietkhampha.bookingservice.dto.PublicTourSlotResponse;
import com.vietkhampha.bookingservice.entity.TourSlot;
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

@Service
public class TourSlotService {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final TourSlotRepository tourSlotRepository;
    private final TourServiceClient tourServiceClient;

    public TourSlotService(TourSlotRepository tourSlotRepository, TourServiceClient tourServiceClient) {
        this.tourSlotRepository = tourSlotRepository;
        this.tourServiceClient = tourServiceClient;
    }

    @Transactional
    public TourSlot createSlot(CreateSlotRequest request) {
        tourServiceClient.requireActiveTour(request.getTourId());

        tourSlotRepository.findByTourIdAndDepartureDate(request.getTourId(), request.getDepartureDate())
                .ifPresent(existing -> {
                    throw new BusinessException(ErrorCode.SLOT_ALREADY_EXISTS);
                });

        TourSlot slot = new TourSlot(request.getTourId(), request.getDepartureDate(), request.getMaxCapacity());
        try {
            return tourSlotRepository.saveAndFlush(slot);
        } catch (DataIntegrityViolationException exception) {
            if (hasSqlState(exception, "23505")) {
                throw new BusinessException(ErrorCode.SLOT_ALREADY_EXISTS);
            }
            throw exception;
        }
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
