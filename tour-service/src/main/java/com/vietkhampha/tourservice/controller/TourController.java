package com.vietkhampha.tourservice.controller;

import com.vietkhampha.tourservice.dto.TourResponse;
import com.vietkhampha.tourservice.dto.TourSearchResponse;
import com.vietkhampha.tourservice.service.TourSearchService;
import com.vietkhampha.tourservice.service.TourService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequestMapping("/v1/tours")
public class TourController {

    private final TourService tourService;
    private final TourSearchService tourSearchService;

    public TourController(TourService tourService, TourSearchService tourSearchService) {
        this.tourService = tourService;
        this.tourSearchService = tourSearchService;
    }

    @GetMapping
    public ResponseEntity<TourSearchResponse> searchTours(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String destination,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) String fromDate,
            @RequestParam(required = false) String toDate,
            @RequestParam(required = false) String tourType,
            @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lng,
            @RequestParam(required = false) Double radiusKm,
            @RequestParam(required = false) String sortBy,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(
                tourSearchService.search(q, destination, minPrice, maxPrice, fromDate, toDate, tourType,
                        lat, lng, radiusKm, sortBy, page, size)
        );
    }

    @GetMapping("/{id}")
    public ResponseEntity<TourResponse> getTourDetail(@PathVariable String id) {
        return ResponseEntity.ok(tourService.getPublicTourById(id));
    }

}
