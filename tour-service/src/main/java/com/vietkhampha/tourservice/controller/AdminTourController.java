package com.vietkhampha.tourservice.controller;

import com.vietkhampha.tourservice.dto.TourRequest;
import com.vietkhampha.tourservice.dto.TourResponse;
import com.vietkhampha.tourservice.service.TourService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/admin/tours")
public class AdminTourController {

    private final TourService tourService;

    public AdminTourController(TourService tourService) {
        this.tourService = tourService;
    }

    @PostMapping
    public ResponseEntity<TourResponse> createTour(@Valid @RequestBody TourRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(tourService.createTour(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<TourResponse> updateTour(@PathVariable String id, @Valid @RequestBody TourRequest request) {
        return ResponseEntity.ok(tourService.updateTour(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deactivateTour(@PathVariable String id) {
        tourService.deactivateTour(id);
        return ResponseEntity.noContent().build(); // 204 — soft-delete thành công, không cần trả nội dung
    }

    @GetMapping("/{id}")
    public ResponseEntity<TourResponse> getTourById(@PathVariable String id) {
        return ResponseEntity.ok(tourService.getTourById(id));
    }

}
