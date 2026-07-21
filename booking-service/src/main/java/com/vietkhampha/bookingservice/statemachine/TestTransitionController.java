package com.vietkhampha.bookingservice.statemachine;

import com.vietkhampha.bookingservice.entity.Booking;
import com.vietkhampha.bookingservice.repository.BookingRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;
@RestController
public class TestTransitionController {

    private final BookingRepository bookingRepository;
    private final BookingStateMachineService stateMachineService;

    public TestTransitionController(BookingRepository bookingRepository, BookingStateMachineService stateMachineService) {
        this.bookingRepository = bookingRepository;
        this.stateMachineService = stateMachineService;
    }

    @PostMapping("/internal/test-transition/{bookingId}")
    @Transactional
    public String testTransition(@PathVariable UUID bookingId, @RequestParam BookingEvent event) {
        Booking booking = bookingRepository.findById(bookingId).orElseThrow();
        stateMachineService.transition(booking, event);
        bookingRepository.save(booking);
        return "Trang thai moi: " + booking.getStatus();
    }
}
