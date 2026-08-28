package com.vietkhampha.bookingservice.dto;

import com.vietkhampha.bookingservice.entity.TourSlot;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record DepartureResponse(
        UUID departureId,
        String tourId,
        LocalDate startDate,
        LocalDate endDate,
        int capacity,
        int reservedSeats,
        int availableSeats,
        String guideId,
        BigDecimal priceOverride,
        String status,
        boolean bookable
) {
    public static DepartureResponse from(TourSlot departure) {
        return new DepartureResponse(
                departure.getId(),
                departure.getTourId(),
                departure.getStartDate(),
                departure.getEndDate(),
                departure.getMaxCapacity(),
                departure.getReservedSeats(),
                departure.getAvailableSlots(),
                departure.getGuideId(),
                departure.getPriceOverride(),
                departure.getEffectiveStatus(),
                departure.getStatus() == TourSlot.Status.OPEN && departure.getAvailableSlots() > 0
        );
    }
}
