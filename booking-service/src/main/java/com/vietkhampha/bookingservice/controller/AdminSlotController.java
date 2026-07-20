package com.vietkhampha.bookingservice.controller;

import com.vietkhampha.bookingservice.dto.CreateSlotRequest;
import com.vietkhampha.bookingservice.entity.TourSlot;
import com.vietkhampha.bookingservice.service.TourSlotService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/admin/tours")
public class AdminSlotController {

    private final TourSlotService tourSlotService;

    public AdminSlotController(TourSlotService tourSlotService) {
        this.tourSlotService = tourSlotService;
    }

    @PostMapping("/{tourId}/slots")
    public ResponseEntity<TourSlot> createSlot(
            @org.springframework.web.bind.annotation.PathVariable String tourId,
            @Valid @RequestBody CreateSlotRequest request
    ) {

        request.setTourId(tourId);
        TourSlot created = tourSlotService.createSlot(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

}