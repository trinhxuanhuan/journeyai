package com.vietkhampha.bookingservice.job;

import com.vietkhampha.bookingservice.entity.Booking;
import com.vietkhampha.bookingservice.repository.BookingRepository;
import com.vietkhampha.bookingservice.service.BookingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
public class BookingExpiryJob {

    private static final Logger log = LoggerFactory.getLogger(BookingExpiryJob.class);

    private final BookingRepository bookingRepository;
    private final BookingService bookingService;

    public BookingExpiryJob(BookingRepository bookingRepository, BookingService bookingService) {
        this.bookingRepository = bookingRepository;
        this.bookingService = bookingService;
    }

    @Scheduled(fixedDelay = 60000)
    public void expireOverdueBookings() {
        List<Booking> overdue = bookingRepository.findByStatusAndHoldExpiresAtBefore(
                Booking.Status.PENDING, Instant.now()
        );

        for (Booking booking : overdue) {
            try {
                bookingService.expireBooking(booking.getId(), "Qua han giu cho 15 phut");
                log.info("Da het han booking {}", booking.getId());
            } catch (Exception e) {
                log.error("Loi khi xu ly het han booking {}, se thu lai o lan quet sau", booking.getId(), e);
            }
        }
    }
}