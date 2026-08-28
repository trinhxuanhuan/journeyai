package com.vietkhampha.bookingservice.controller;

import com.vietkhampha.bookingservice.dto.AdminBookingItemDto;
import com.vietkhampha.bookingservice.dto.AdminBookingListResponse;
import com.vietkhampha.bookingservice.entity.Booking;
import com.vietkhampha.bookingservice.repository.BookingRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;
import java.util.stream.Collectors;
import com.vietkhampha.bookingservice.dto.AssignGuideRequest;
import com.vietkhampha.bookingservice.dto.BookingResponse;
import com.vietkhampha.bookingservice.service.BookingService;
import jakarta.validation.Valid;
@RestController
@RequestMapping("/v1/admin/bookings")
public class AdminBookingController {

    private final BookingRepository bookingRepository;
    private final BookingService bookingService;

    public AdminBookingController(BookingRepository bookingRepository, BookingService bookingService) {
        this.bookingRepository = bookingRepository;
        this.bookingService = bookingService;
    }

    @GetMapping
    public ResponseEntity<AdminBookingListResponse> listBookings(
            @RequestParam(required = false) String tourId,
            @RequestParam(required = false) LocalDate departureDate,
            @RequestParam(required = false) Booking.Status status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Page<Booking> result = bookingRepository.findByFilters(
                tourId, departureDate, status, PageRequest.of(page, size)
        );

        var items = result.getContent().stream()
                .map(AdminBookingItemDto::from)
                .collect(Collectors.toList());

        return ResponseEntity.ok(new AdminBookingListResponse(items, result.getTotalElements(), page));
    }

    @PatchMapping("/{bookingId}/guide")
    public ResponseEntity<BookingResponse> assignGuide(
            @PathVariable UUID bookingId,
            @Valid @RequestBody AssignGuideRequest request
    ) {
        return ResponseEntity.ok(bookingService.assignPrivateGuide(bookingId, request.getGuideId()));
    }

}
