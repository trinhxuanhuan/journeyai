package com.vietkhampha.tourservice.controller;

import com.vietkhampha.tourservice.dto.TourResponse;
import com.vietkhampha.tourservice.service.TourService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/tours")
public class TourController {

    private final TourService tourService;

    public TourController(TourService tourService) {
        this.tourService = tourService;
    }

    @GetMapping("/{id}")
    public ResponseEntity<TourResponse> getTourDetail(@PathVariable String id) {
        return ResponseEntity.ok(tourService.getPublicTourById(id));
    }

}