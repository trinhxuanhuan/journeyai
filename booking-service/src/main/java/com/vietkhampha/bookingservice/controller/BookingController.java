package com.vietkhampha.bookingservice.controller;

import com.vietkhampha.bookingservice.dto.BookingResponse;
import com.vietkhampha.bookingservice.dto.CreateBookingRequest;
import com.vietkhampha.bookingservice.dto.CreateBookingResponse;
import com.vietkhampha.bookingservice.dto.CustomerBookingListResponse;
import com.vietkhampha.bookingservice.entity.Booking;
import com.vietkhampha.bookingservice.exception.BusinessException;
import com.vietkhampha.bookingservice.exception.ErrorCode;
import com.vietkhampha.bookingservice.repository.BookingRepository;
import com.vietkhampha.bookingservice.service.BookingService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/v1/bookings")
public class BookingController {

    private final BookingService bookingService;
    private final BookingRepository bookingRepository;

    public BookingController(BookingService bookingService, BookingRepository bookingRepository) {
        this.bookingService = bookingService;
        this.bookingRepository = bookingRepository;
    }

    @PostMapping
    public ResponseEntity<CreateBookingResponse> createBooking(
            @RequestHeader("X-User-Id") String userIdHeader,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreateBookingRequest request
    ) {
        UUID customerId = UUID.fromString(userIdHeader);
        BookingService.BookingResult result = bookingService.createBooking(customerId, idempotencyKey, request);

        HttpStatus status = result.isReplay() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(result.response());
    }

    @GetMapping("/me")
    public ResponseEntity<CustomerBookingListResponse> getMyBookings(
            @RequestHeader("X-User-Id") String userIdHeader,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        UUID customerId = UUID.fromString(userIdHeader);
        return ResponseEntity.ok(bookingService.getCustomerBookings(customerId, page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<BookingResponse> getBooking(
            @RequestHeader("X-User-Id") String userIdHeader,
            @PathVariable UUID id
    ) {
        UUID customerId = UUID.fromString(userIdHeader);
        Booking booking = bookingRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.BOOKING_NOT_FOUND));

        if (!booking.getCustomerId().equals(customerId)) {
            throw new BusinessException(ErrorCode.BOOKING_NOT_FOUND);
        }

        return ResponseEntity.ok(BookingResponse.from(booking));
    }
    @PostMapping("/{id}/cancel")
    public ResponseEntity<Void> cancelBooking(
            @RequestHeader("X-User-Id") String userIdHeader,
            @PathVariable UUID id
    ) {
        UUID customerId = UUID.fromString(userIdHeader);
        bookingService.cancelBooking(id, customerId);
        return ResponseEntity.noContent().build();
    }

}
