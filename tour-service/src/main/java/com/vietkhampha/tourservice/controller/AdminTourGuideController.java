package com.vietkhampha.tourservice.controller;

import com.vietkhampha.tourservice.dto.CreateTourGuideRequest;
import com.vietkhampha.tourservice.dto.TourGuideResponse;
import com.vietkhampha.tourservice.service.TourGuideService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/v1/admin/tour-guides")
public class AdminTourGuideController {

    private final TourGuideService tourGuideService;

    public AdminTourGuideController(TourGuideService tourGuideService) {
        this.tourGuideService = tourGuideService;
    }

    @PostMapping
    public ResponseEntity<TourGuideResponse> createTourGuide(
            @Valid @RequestBody CreateTourGuideRequest request
    ) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(tourGuideService.createTourGuide(request));
    }

    @GetMapping
    public ResponseEntity<List<TourGuideResponse>> listTourGuides() {
        return ResponseEntity.ok(tourGuideService.listTourGuides());
    }

    @GetMapping("/{id}")
    public ResponseEntity<TourGuideResponse> getTourGuide(@PathVariable String id) {
        return ResponseEntity.ok(tourGuideService.getTourGuide(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deactivateTourGuide(@PathVariable String id) {
        tourGuideService.deactivateTourGuide(id);
        return ResponseEntity.noContent().build();
    }
}
