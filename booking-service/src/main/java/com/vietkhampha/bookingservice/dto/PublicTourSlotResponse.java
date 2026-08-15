package com.vietkhampha.bookingservice.dto;

import com.vietkhampha.bookingservice.entity.TourSlot;

import java.time.LocalDate;
import java.util.UUID;

public record PublicTourSlotResponse(
        UUID slotId,
        LocalDate departureDate,
        int availableSlots,
        boolean bookable
) {
    public static PublicTourSlotResponse from(TourSlot slot) {
        int availableSlots = Math.max(0, slot.getAvailableSlots());
        return new PublicTourSlotResponse(
                slot.getId(),
                slot.getDepartureDate(),
                availableSlots,
                availableSlots > 0
        );
    }
}
