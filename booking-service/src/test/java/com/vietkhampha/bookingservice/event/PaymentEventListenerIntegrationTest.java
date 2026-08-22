package com.vietkhampha.bookingservice.event;

import com.vietkhampha.bookingservice.entity.Booking;
import com.vietkhampha.bookingservice.entity.OutboxEvent;
import com.vietkhampha.bookingservice.entity.TourSlot;
import com.vietkhampha.bookingservice.outbox.OutboxPoller;
import com.vietkhampha.bookingservice.repository.BookingRepository;
import com.vietkhampha.bookingservice.repository.OutboxEventRepository;
import com.vietkhampha.bookingservice.repository.TourSlotRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "app.outbox.poller.enabled=false",
        "spring.kafka.listener.auto-startup=false",
        "spring.kafka.bootstrap-servers=localhost:1"
})
@Testcontainers
class PaymentEventListenerIntegrationTest {

    private static final int PARTICIPANT_COUNT = 2;
    private static final BigDecimal BOOKING_AMOUNT = new BigDecimal("1250000.00");

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private PaymentEventListener paymentEventListener;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private TourSlotRepository tourSlotRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ApplicationContext applicationContext;

    private Booking booking;
    private TourSlot slot;

    @BeforeEach
    void setUp() {
        assertThat(applicationContext.getBeansOfType(OutboxPoller.class)).isEmpty();
        dropOutboxFailureConstraint();
        jdbcTemplate.update("DELETE FROM processed_payment_events");
        outboxEventRepository.deleteAll();
        bookingRepository.deleteAll();
        tourSlotRepository.deleteAll();

        TourSlot newSlot = new TourSlot("payment-event-tour", LocalDate.now().plusDays(30), 10);
        newSlot.reserve(PARTICIPANT_COUNT);
        slot = tourSlotRepository.saveAndFlush(newSlot);
        booking = bookingRepository.saveAndFlush(new Booking(
                UUID.randomUUID(),
                slot.getId(),
                PARTICIPANT_COUNT,
                BOOKING_AMOUNT
        ));
    }

    @AfterEach
    void tearDown() {
        dropOutboxFailureConstraint();
    }

    @Test
    void succeededEventConfirmsBookingAndCommitsInboxAndOutboxAtomically() {
        UUID eventId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();

        paymentEventListener.handlePaymentEvent(paymentEvent(
                eventId,
                "payment.succeeded",
                paymentId,
                booking.getId()
        ));

        assertThat(reloadBooking().getStatus()).isEqualTo(Booking.Status.CONFIRMED);
        assertThat(reloadSlot().getBookedCount()).isEqualTo(PARTICIPANT_COUNT);
        assertProcessedEvent(eventId, paymentId, booking.getId(), "payment.succeeded");
        assertSingleBookingEvent("booking.confirmed");
    }

    @Test
    void replayAndDifferentEventIdForSamePaymentOutcomeDoNotRepeatSideEffects() {
        UUID eventId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        Map<String, Object> event = paymentEvent(eventId, "payment.succeeded", paymentId, booking.getId());

        paymentEventListener.handlePaymentEvent(event);
        paymentEventListener.handlePaymentEvent(event);
        paymentEventListener.handlePaymentEvent(paymentEvent(
                UUID.randomUUID(),
                "payment.succeeded",
                paymentId,
                booking.getId()
        ));

        assertThat(reloadBooking().getStatus()).isEqualTo(Booking.Status.CONFIRMED);
        assertThat(processedEventCount()).isEqualTo(1);
        assertSingleBookingEvent("booking.confirmed");
    }

    @Test
    void contradictoryOutcomeForSamePaymentIsRejectedAsContractCollision() {
        UUID paymentId = UUID.randomUUID();
        paymentEventListener.handlePaymentEvent(paymentEvent(
                UUID.randomUUID(),
                "payment.succeeded",
                paymentId,
                booking.getId()
        ));

        assertThatThrownBy(() -> paymentEventListener.handlePaymentEvent(paymentEvent(
                UUID.randomUUID(),
                "payment.failed",
                paymentId,
                booking.getId()
        )))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("collides");

        assertThat(reloadBooking().getStatus()).isEqualTo(Booking.Status.CONFIRMED);
        assertThat(reloadSlot().getBookedCount()).isEqualTo(PARTICIPANT_COUNT);
        assertThat(processedEventCount()).isEqualTo(1);
        assertSingleBookingEvent("booking.confirmed");
    }

    @Test
    void reusedEventIdIsRejectedEvenWhenPaymentIdMatchesAnotherProcessedEvent() {
        UUID reusedEventId = UUID.randomUUID();
        UUID firstPaymentId = UUID.randomUUID();
        UUID secondPaymentId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO processed_payment_events (event_id, payment_id, booking_id, event_type)
                VALUES (?, ?, ?, 'payment.succeeded'), (?, ?, ?, 'payment.succeeded')
                """,
                reusedEventId, firstPaymentId, booking.getId(),
                UUID.randomUUID(), secondPaymentId, booking.getId()
        );

        assertThatThrownBy(() -> paymentEventListener.handlePaymentEvent(paymentEvent(
                reusedEventId,
                "payment.succeeded",
                secondPaymentId,
                booking.getId()
        )))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("collides");

        assertThat(reloadBooking().getStatus()).isEqualTo(Booking.Status.PENDING);
        assertThat(processedEventCount()).isEqualTo(2);
        assertThat(outboxEventsForBooking()).isEmpty();
    }

    @Test
    void concurrentDuplicateDeliveryProducesOneBookingTransitionAndOneOutboxEvent() throws Exception {
        Map<String, Object> event = paymentEvent(
                UUID.randomUUID(),
                "payment.succeeded",
                UUID.randomUUID(),
                booking.getId()
        );
        CyclicBarrier startBarrier = new CyclicBarrier(3);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = executor.submit(() -> consumeAfterBarrier(event, startBarrier));
            Future<?> second = executor.submit(() -> consumeAfterBarrier(event, startBarrier));
            startBarrier.await(5, TimeUnit.SECONDS);

            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);

            assertThat(reloadBooking().getStatus()).isEqualTo(Booking.Status.CONFIRMED);
            assertThat(processedEventCount()).isEqualTo(1);
            assertSingleBookingEvent("booking.confirmed");
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void failedEventReleasesCapacityOnceAndReplayIsIdempotent() {
        Map<String, Object> event = paymentEvent(
                UUID.randomUUID(),
                "payment.failed",
                UUID.randomUUID(),
                booking.getId()
        );

        paymentEventListener.handlePaymentEvent(event);
        paymentEventListener.handlePaymentEvent(event);

        assertThat(reloadBooking().getStatus()).isEqualTo(Booking.Status.PAYMENT_FAILED);
        assertThat(reloadSlot().getBookedCount()).isZero();
        assertThat(processedEventCount()).isEqualTo(1);
        assertSingleBookingEvent("booking.payment_failed");
    }

    @Test
    void downstreamFailureRollsBackInboxBookingAndOutboxSoKafkaCanRetry() {
        jdbcTemplate.execute("""
                ALTER TABLE outbox_events
                ADD CONSTRAINT reject_booking_confirmed_for_payment_event_test
                CHECK (event_type <> 'booking.confirmed')
                """);
        Map<String, Object> event = paymentEvent(
                UUID.randomUUID(),
                "payment.succeeded",
                UUID.randomUUID(),
                booking.getId()
        );

        assertThatThrownBy(() -> paymentEventListener.handlePaymentEvent(event))
                .isInstanceOf(RuntimeException.class);

        assertThat(reloadBooking().getStatus()).isEqualTo(Booking.Status.PENDING);
        assertThat(reloadSlot().getBookedCount()).isEqualTo(PARTICIPANT_COUNT);
        assertThat(processedEventCount()).isZero();
        assertThat(outboxEventsForBooking()).isEmpty();

        dropOutboxFailureConstraint();
        paymentEventListener.handlePaymentEvent(event);

        assertThat(reloadBooking().getStatus()).isEqualTo(Booking.Status.CONFIRMED);
        assertThat(processedEventCount()).isEqualTo(1);
        assertSingleBookingEvent("booking.confirmed");
    }

    @Test
    void mismatchedAggregateIdIsRejectedBeforeDatabaseMutation() {
        Map<String, Object> event = paymentEvent(
                UUID.randomUUID(),
                "payment.succeeded",
                UUID.randomUUID(),
                booking.getId()
        );
        event = Map.of(
                "eventId", event.get("eventId"),
                "eventType", event.get("eventType"),
                "aggregateId", UUID.randomUUID().toString(),
                "occurredAt", event.get("occurredAt"),
                "payload", event.get("payload")
        );
        Map<String, Object> invalidEvent = event;

        assertThatThrownBy(() -> paymentEventListener.handlePaymentEvent(invalidEvent))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("aggregateId");

        assertThat(reloadBooking().getStatus()).isEqualTo(Booking.Status.PENDING);
        assertThat(processedEventCount()).isZero();
        assertThat(outboxEventsForBooking()).isEmpty();
    }

    @Test
    void legacyEnvelopeWithoutEventIdUsesDeterministicIdentityAndRemainsReplaySafe() {
        UUID paymentId = UUID.randomUUID();
        Map<String, Object> legacyEvent = Map.of(
                "eventType", "payment.succeeded",
                "payload", Map.of(
                        "paymentId", paymentId.toString(),
                        "bookingId", booking.getId().toString()
                )
        );

        paymentEventListener.handlePaymentEvent(legacyEvent);
        paymentEventListener.handlePaymentEvent(legacyEvent);

        assertThat(reloadBooking().getStatus()).isEqualTo(Booking.Status.CONFIRMED);
        assertThat(processedEventCount()).isEqualTo(1);
        assertSingleBookingEvent("booking.confirmed");
    }

    private Map<String, Object> paymentEvent(
            UUID eventId,
            String eventType,
            UUID paymentId,
            UUID bookingId
    ) {
        return Map.of(
                "eventId", eventId.toString(),
                "eventType", eventType,
                "aggregateId", paymentId.toString(),
                "occurredAt", Instant.parse("2026-08-22T07:00:00Z").toString(),
                "payload", Map.of(
                        "paymentId", paymentId.toString(),
                        "bookingId", bookingId.toString(),
                        "gateway", "VNPAY",
                        "amount", BOOKING_AMOUNT,
                        "gatewayTransactionRef", "TEST_TXN_001"
                )
        );
    }

    private void consumeAfterBarrier(Map<String, Object> event, CyclicBarrier barrier) {
        try {
            barrier.await(5, TimeUnit.SECONDS);
            paymentEventListener.handlePaymentEvent(event);
        } catch (Exception exception) {
            throw new IllegalStateException("Concurrent payment event consumer failed", exception);
        }
    }

    private void assertProcessedEvent(
            UUID eventId,
            UUID paymentId,
            UUID bookingId,
            String eventType
    ) {
        Map<String, Object> row = jdbcTemplate.queryForMap("""
                SELECT event_id, payment_id, booking_id, event_type, processed_at
                FROM processed_payment_events
                """);
        assertThat(row)
                .containsEntry("event_id", eventId)
                .containsEntry("payment_id", paymentId)
                .containsEntry("booking_id", bookingId)
                .containsEntry("event_type", eventType);
        assertThat(row.get("processed_at")).isNotNull();
    }

    private int processedEventCount() {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM processed_payment_events",
                Integer.class
        );
    }

    private void assertSingleBookingEvent(String eventType) {
        assertThat(outboxEventsForBooking())
                .singleElement()
                .extracting(OutboxEvent::getEventType)
                .isEqualTo(eventType);
    }

    private List<OutboxEvent> outboxEventsForBooking() {
        return outboxEventRepository.findAll().stream()
                .filter(event -> booking.getId().equals(event.getAggregateId()))
                .toList();
    }

    private Booking reloadBooking() {
        return bookingRepository.findById(booking.getId()).orElseThrow();
    }

    private TourSlot reloadSlot() {
        return tourSlotRepository.findById(slot.getId()).orElseThrow();
    }

    private void dropOutboxFailureConstraint() {
        jdbcTemplate.execute("""
                ALTER TABLE outbox_events
                DROP CONSTRAINT IF EXISTS reject_booking_confirmed_for_payment_event_test
                """);
    }
}
