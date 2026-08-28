package com.vietkhampha.bookingservice.service;

import com.vietkhampha.bookingservice.entity.Booking;
import com.vietkhampha.bookingservice.entity.OutboxEvent;
import com.vietkhampha.bookingservice.entity.TourSlot;
import com.vietkhampha.bookingservice.exception.BusinessException;
import com.vietkhampha.bookingservice.outbox.OutboxPoller;
import com.vietkhampha.bookingservice.repository.BookingRepository;
import com.vietkhampha.bookingservice.repository.OutboxEventRepository;
import com.vietkhampha.bookingservice.repository.TourSlotRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {
        "app.outbox.poller.enabled=false",
        "spring.kafka.listener.auto-startup=false"
})
@Testcontainers
class BookingConcurrencyTest {

    private static final long WORKER_TIMEOUT_SECONDS = 10;

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

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID bookingId;

    @BeforeEach
    void setUp() {
        assertTrue(applicationContext.getBeansOfType(OutboxPoller.class).isEmpty());

        TourSlot slot = new TourSlot("test-tour-id", LocalDate.now().plusDays(30), 10);
        TourSlot savedSlot = tourSlotRepository.save(slot);
        savedSlot.reserve(1); // Giả lập đã trừ 1 chỗ khi tạo booking
        tourSlotRepository.save(savedSlot);

        bookingId = UUID.randomUUID();
        Instant holdExpiresAt = Instant.now().minus(Duration.ofMinutes(5));
        Instant createdAt = holdExpiresAt.minus(Duration.ofMinutes(15));
        jdbcTemplate.update("""
                INSERT INTO bookings (
                    id, created_at, customer_id, generated_itinerary_id, hold_expires_at,
                    participant_count, status, total_amount, tour_slot_id, updated_at,
                    tour_id, booking_type, start_date, end_date, price_model, unit_price,
                    commercial_snapshot, cancellation_policy_snapshot,
                    guide_option_selected, single_room_count
                ) VALUES (?, ?, ?, NULL, ?, 1, 'PENDING', ?, ?, ?,
                    ?, 'GROUP', ?, ?, 'PER_PERSON', ?, '{}',
                    '[{"minimumDaysBeforeDeparture":0,"refundPercentage":0}]', false, 0)
                """,
                bookingId,
                createdAt.atOffset(ZoneOffset.UTC),
                UUID.randomUUID(),
                holdExpiresAt.atOffset(ZoneOffset.UTC),
                BigDecimal.valueOf(1000000),
                savedSlot.getId(),
                createdAt.atOffset(ZoneOffset.UTC),
                savedSlot.getTourId(),
                savedSlot.getDepartureDate(),
                savedSlot.getEndDate(),
                BigDecimal.valueOf(1000000)
        );
    }

    @AfterEach
    void tearDown() {
        outboxEventRepository.deleteAll();
        bookingRepository.deleteAll();
        tourSlotRepository.deleteAll();
    }

    @RepeatedTest(50)
    void expireAndConfirmPayment_concurrently_mustNotCorruptState() throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch readyLatch = new CountDownLatch(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        boolean workersReady;
        TaskOutcome expireOutcome;
        TaskOutcome confirmOutcome;

        try {
            Future<Void> expireFuture = executor.submit(() -> {
                readyLatch.countDown();
                if (!startLatch.await(WORKER_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Expire worker did not receive the coordinated start signal");
                }
                bookingService.expireBooking(bookingId, "Test concurrent expire");
                return null;
            });
            Future<Void> confirmFuture = executor.submit(() -> {
                readyLatch.countDown();
                if (!startLatch.await(WORKER_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Confirm worker did not receive the coordinated start signal");
                }
                bookingService.confirmBookingPayment(bookingId);
                return null;
            });

            workersReady = readyLatch.await(WORKER_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            startLatch.countDown();

            expireOutcome = awaitOutcome(expireFuture);
            confirmOutcome = awaitOutcome(confirmFuture);
        } finally {
            startLatch.countDown();
            executor.shutdownNow();
            assertTrue(
                    executor.awaitTermination(WORKER_TIMEOUT_SECONDS, TimeUnit.SECONDS),
                    "Concurrent booking workers did not terminate cleanly"
            );
        }

        assertTrue(workersReady, "Both workers must be ready before releasing the coordinated start");
        assertAll(
                () -> assertTaskSucceeded("expire", expireOutcome),
                () -> assertTaskSucceeded("confirm", confirmOutcome)
        );

        List<String> eventTypes = outboxEventRepository.findAll().stream()
                .filter(event -> bookingId.equals(event.getAggregateId()))
                .map(OutboxEvent::getEventType)
                .sorted()
                .toList();

        RaceWinner winner;
        if (eventTypes.equals(List.of("booking.confirmed"))) {
            winner = RaceWinner.CONFIRM;
        } else if (eventTypes.equals(List.of("booking.expired", "booking.late_payment_recovered"))) {
            winner = RaceWinner.EXPIRE;
        } else {
            throw new AssertionError("Unexpected outbox outcome for booking race: " + eventTypes);
        }

        Booking finalBooking = bookingRepository.findById(bookingId).orElseThrow();
        TourSlot finalSlot = tourSlotRepository.findById(finalBooking.getTourSlotId()).orElseThrow();

        assertAll(
                () -> assertEquals(
                        Booking.Status.CONFIRMED,
                        finalBooking.getStatus(),
                        "Final booking status must be CONFIRMED when " + winner + " acquired the initial lock"
                ),
                () -> assertEquals(
                        1,
                        finalSlot.getBookedCount(),
                        "The confirmed booking must retain exactly one reserved slot when "
                                + winner + " acquired the initial lock"
                )
        );
    }

    private TaskOutcome awaitOutcome(Future<Void> future) throws InterruptedException {
        try {
            future.get(WORKER_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            return TaskOutcome.succeeded();
        } catch (ExecutionException exception) {
            return TaskOutcome.failed(exception.getCause());
        } catch (TimeoutException exception) {
            future.cancel(true);
            return TaskOutcome.failed(exception);
        }
    }

    private void assertTaskSucceeded(String taskName, TaskOutcome outcome) {
        if (outcome.failure() == null) {
            return;
        }
        if (outcome.failure() instanceof BusinessException businessException) {
            throw new AssertionError(
                    taskName + " task returned unexpected business error " + businessException.getErrorCode(),
                    businessException
            );
        }
        throw new AssertionError(taskName + " task failed with a technical error", outcome.failure());
    }

    private enum RaceWinner {
        CONFIRM,
        EXPIRE
    }

    private record TaskOutcome(Throwable failure) {

        private static TaskOutcome succeeded() {
            return new TaskOutcome(null);
        }

        private static TaskOutcome failed(Throwable failure) {
            return new TaskOutcome(failure);
        }
    }
}
