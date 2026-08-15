package com.vietkhampha.bookingservice.controller;

import com.vietkhampha.bookingservice.dto.PublicTourSlotResponse;
import com.vietkhampha.bookingservice.service.TourSlotService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/v1/tours")
public class PublicTourSlotController {

    private final TourSlotService tourSlotService;

    public PublicTourSlotController(TourSlotService tourSlotService) {
        this.tourSlotService = tourSlotService;
    }

    @GetMapping("/{tourId}/slots")
    public ResponseEntity<List<PublicTourSlotResponse>> getTourSlots(@PathVariable String tourId) {
        return ResponseEntity.ok(tourSlotService.getPublicSlots(tourId));
    }
}
