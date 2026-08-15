package com.vietkhampha.bookingservice.service;

import com.vietkhampha.bookingservice.dto.CreateSlotRequest;
import com.vietkhampha.bookingservice.dto.PublicTourSlotResponse;
import com.vietkhampha.bookingservice.entity.TourSlot;
import com.vietkhampha.bookingservice.exception.BusinessException;
import com.vietkhampha.bookingservice.exception.ErrorCode;
import com.vietkhampha.bookingservice.repository.TourSlotRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@Service
public class TourSlotService {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final TourSlotRepository tourSlotRepository;

    public TourSlotService(TourSlotRepository tourSlotRepository) {
        this.tourSlotRepository = tourSlotRepository;
    }

    @Transactional
    public TourSlot createSlot(CreateSlotRequest request) {

        tourSlotRepository.findByTourIdAndDepartureDate(request.getTourId(), request.getDepartureDate())
                .ifPresent(existing -> {
                    throw new BusinessException(ErrorCode.SLOT_ALREADY_EXISTS);
                });

        TourSlot slot = new TourSlot(request.getTourId(), request.getDepartureDate(), request.getMaxCapacity());
        return tourSlotRepository.save(slot);
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
}
