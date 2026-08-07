package com.vietkhampha.bookingservice.service;

import com.vietkhampha.bookingservice.entity.Booking;
import com.vietkhampha.bookingservice.entity.TourSlot;
import com.vietkhampha.bookingservice.repository.BookingRepository;
import com.vietkhampha.bookingservice.repository.TourSlotRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest
@Testcontainers
class BookingConcurrencyTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void kafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", () -> "localhost:1");
    }

    @Autowired
    private BookingService bookingService;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private TourSlotRepository tourSlotRepository;

    private UUID bookingId;

    @BeforeEach
    void setUp() {
        TourSlot slot = new TourSlot("test-tour-id", LocalDate.now().plusDays(30), 10);
        TourSlot savedSlot = tourSlotRepository.save(slot);
        savedSlot.reserve(1); // Giả lập đã trừ 1 chỗ khi tạo booking
        tourSlotRepository.save(savedSlot);

        Booking booking = new Booking(UUID.randomUUID(), savedSlot.getId(), 1, BigDecimal.valueOf(1000000));
        bookingId = bookingRepository.save(booking).getId();
    }

    @AfterEach
    void tearDown() {
        bookingRepository.deleteAll();
        tourSlotRepository.deleteAll();
    }
    @RepeatedTest(50)
    void expireAndConfirmPayment_concurrently_mustNotCorruptState() throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch readyLatch = new CountDownLatch(2);
        CountDownLatch startLatch = new CountDownLatch(1);

        Runnable expireTask = () -> {
            readyLatch.countDown();
            await(startLatch);
            try {
                bookingService.expireBooking(bookingId, "Test concurrent expire");
            } catch (Exception ignored) {
            }
        };

        Runnable confirmTask = () -> {
            readyLatch.countDown();
            await(startLatch);
            try {
                bookingService.confirmBookingPayment(bookingId);
            } catch (Exception ignored) {
            }
        };

        executor.submit(expireTask);
        executor.submit(confirmTask);
        readyLatch.await(2, TimeUnit.SECONDS);
        startLatch.countDown();

        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);
        Booking finalBooking = bookingRepository.findById(bookingId).orElseThrow();
        assertTrue(
                finalBooking.getStatus() == Booking.Status.CONFIRMED
                        || finalBooking.getStatus() == Booking.Status.EXPIRED,
                "Trang thai cuoi cung phai la CONFIRMED hoac EXPIRED, thuc te: " + finalBooking.getStatus()
        );

        TourSlot finalSlot = tourSlotRepository.findById(finalBooking.getTourSlotId()).orElseThrow();
        if (finalBooking.getStatus() == Booking.Status.CONFIRMED) {
            assertEquals(1, finalSlot.getBookedCount(),
                    "Booking CONFIRMED nhung slot da bi hoan nham - DU LIEU KHONG NHAT QUAN");
        } else {
            assertEquals(0, finalSlot.getBookedCount(),
                    "Booking EXPIRED nhung slot chua duoc hoan - DU LIEU KHONG NHAT QUAN");
        }
    }

    private void await(CountDownLatch latch) {
        try {
            latch.await(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}