package com.vietkhampha.bookingservice.controller;

import com.vietkhampha.bookingservice.dto.DepartureResponse;
import com.vietkhampha.bookingservice.service.TourSlotService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class PublicDepartureController {
    private final TourSlotService tourSlotService;

    public PublicDepartureController(TourSlotService tourSlotService) {
        this.tourSlotService = tourSlotService;
    }

    @GetMapping("/v1/tours/{tourId}/departures")
    public ResponseEntity<List<DepartureResponse>> getDepartures(@PathVariable String tourId) {
        return ResponseEntity.ok(tourSlotService.getPublicDepartures(tourId));
    }
}
