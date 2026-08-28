package com.vietkhampha.bookingservice.controller;

import com.vietkhampha.bookingservice.dto.CreateDepartureRequest;
import com.vietkhampha.bookingservice.dto.DepartureResponse;
import com.vietkhampha.bookingservice.dto.UpdateDepartureRequest;
import com.vietkhampha.bookingservice.service.TourSlotService;
import com.vietkhampha.bookingservice.service.BookingService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
public class AdminDepartureController {
    private final TourSlotService tourSlotService;
    private final BookingService bookingService;

    public AdminDepartureController(TourSlotService tourSlotService, BookingService bookingService) {
        this.tourSlotService = tourSlotService;
        this.bookingService = bookingService;
    }

    @PostMapping("/v1/admin/tours/{tourId}/departures")
    public ResponseEntity<DepartureResponse> createDeparture(
            @PathVariable String tourId,
            @Valid @RequestBody CreateDepartureRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(tourSlotService.createDeparture(tourId, request));
    }

    @GetMapping("/v1/admin/tours/{tourId}/departures")
    public ResponseEntity<List<DepartureResponse>> listDepartures(@PathVariable String tourId) {
        return ResponseEntity.ok(tourSlotService.getAdminDepartures(tourId));
    }

    @PatchMapping("/v1/admin/departures/{departureId}")
    public ResponseEntity<DepartureResponse> updateDeparture(
            @PathVariable UUID departureId,
            @Valid @RequestBody UpdateDepartureRequest request
    ) {
        return ResponseEntity.ok(tourSlotService.updateDeparture(departureId, request));
    }

    @PostMapping("/v1/admin/departures/{departureId}/cancel")
    public ResponseEntity<Void> cancelDeparture(@PathVariable UUID departureId) {
        bookingService.cancelDeparture(departureId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/v1/admin/departures/{departureId}/complete")
    public ResponseEntity<Void> completeDeparture(@PathVariable UUID departureId) {
        bookingService.completeDeparture(departureId);
        return ResponseEntity.noContent().build();
    }
}
