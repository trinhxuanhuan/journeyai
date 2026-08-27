package com.vietkhampha.bookingservice.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vietkhampha.bookingservice.entity.Booking;
import com.vietkhampha.bookingservice.entity.TourSlot;
import com.vietkhampha.bookingservice.outbox.OutboxPoller;
import com.vietkhampha.bookingservice.repository.BookingRepository;
import com.vietkhampha.bookingservice.repository.OutboxEventRepository;
import com.vietkhampha.bookingservice.repository.TourSlotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "app.outbox.poller.enabled=false",
                "spring.kafka.listener.auto-startup=false",
                "spring.kafka.bootstrap-servers=localhost:1"
        }
)
@Testcontainers
class CustomerBookingHistoryIntegrationTest {

    private static final BigDecimal TOTAL_AMOUNT = new BigDecimal("2500000.00");

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private TourSlotRepository tourSlotRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ApplicationContext applicationContext;

    private TourSlot slot;

    @BeforeEach
    void setUp() {
        assertThat(applicationContext.getBeansOfType(OutboxPoller.class)).isEmpty();
        jdbcTemplate.execute("""
                TRUNCATE TABLE
                    booking_participants,
                    idempotency_keys,
                    outbox_events,
                    processed_payment_events,
                    bookings,
                    tour_slots
                """);
        slot = tourSlotRepository.saveAndFlush(
                new TourSlot("customer-history-tour", LocalDate.now().plusDays(30), 20)
        );
    }

    @Test
    void returnsOnlyAuthenticatedCustomersBookingsNewestFirstWithStablePagination() throws Exception {
        UUID customerId = UUID.randomUUID();
        UUID otherCustomerId = UUID.randomUUID();
        Booking oldest = createBooking(customerId, Booking.Status.PENDING, Instant.parse("2026-08-01T01:00:00Z"));
        Booking middle = createBooking(customerId, Booking.Status.CONFIRMED, Instant.parse("2026-08-02T01:00:00Z"));
        Booking newest = createBooking(customerId, Booking.Status.PAYMENT_FAILED, Instant.parse("2026-08-03T01:00:00Z"));
        createBooking(otherCustomerId, Booking.Status.CONFIRMED, Instant.parse("2026-08-04T01:00:00Z"));

        JsonNode firstPage = getMyBookings(customerId, 0, 2, HttpStatus.OK);

        assertThat(firstPage.path("total").asLong()).isEqualTo(3);
        assertThat(firstPage.path("page").asInt()).isZero();
        assertThat(firstPage.path("size").asInt()).isEqualTo(2);
        assertThat(firstPage.path("totalPages").asInt()).isEqualTo(2);
        assertThat(firstPage.path("items")).hasSize(2);
        assertThat(firstPage.path("items").get(0).path("bookingId").asText())
                .isEqualTo(newest.getId().toString());
        assertThat(firstPage.path("items").get(0).path("status").asText())
                .isEqualTo("PAYMENT_FAILED");
        assertThat(firstPage.path("items").get(1).path("bookingId").asText())
                .isEqualTo(middle.getId().toString());
        assertThat(firstPage.path("items").get(0).has("customerId")).isFalse();

        JsonNode secondPage = getMyBookings(customerId, 1, 2, HttpStatus.OK);

        assertThat(secondPage.path("items")).hasSize(1);
        assertThat(secondPage.path("items").get(0).path("bookingId").asText())
                .isEqualTo(oldest.getId().toString());
    }

    @Test
    void customerWithoutBookingsReceivesAnEmptyPage() throws Exception {
        JsonNode response = getMyBookings(UUID.randomUUID(), 0, 20, HttpStatus.OK);

        assertThat(response.path("items")).isEmpty();
        assertThat(response.path("total").asLong()).isZero();
        assertThat(response.path("page").asInt()).isZero();
        assertThat(response.path("size").asInt()).isEqualTo(20);
        assertThat(response.path("totalPages").asInt()).isZero();
    }

    @Test
    void invalidPaginationIsRejectedWithAStableBusinessError() throws Exception {
        JsonNode negativePage = getMyBookings(UUID.randomUUID(), -1, 20, HttpStatus.BAD_REQUEST);
        JsonNode oversizedPage = getMyBookings(UUID.randomUUID(), 0, 101, HttpStatus.BAD_REQUEST);

        assertThat(negativePage.path("error").asText()).isEqualTo("PAGINATION_INVALID");
        assertThat(oversizedPage.path("error").asText()).isEqualTo("PAGINATION_INVALID");
    }

    @Test
    void cancellationUsesBookingPolicySnapshot_andReleasesGroupSeats() throws Exception {
        UUID customerId = UUID.randomUUID();
        slot.reserve(2);
        tourSlotRepository.saveAndFlush(slot);
        Booking booking = new Booking(
                customerId,
                slot.getTourId(),
                Booking.BookingType.GROUP,
                slot.getId(),
                slot.getDepartureDate(),
                slot.getEndDate(),
                2,
                Booking.PriceModel.PER_PERSON,
                TOTAL_AMOUNT.divide(BigDecimal.valueOf(2)),
                TOTAL_AMOUNT,
                "{}",
                "[{\"minimumDaysBeforeDeparture\":7,\"refundPercentage\":80},"
                        + "{\"minimumDaysBeforeDeparture\":0,\"refundPercentage\":10}]",
                false,
                0
        );
        booking.setStatus(Booking.Status.CONFIRMED);
        Booking saved = bookingRepository.saveAndFlush(booking);

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-User-Id", customerId.toString());
        ResponseEntity<Void> response = restTemplate.exchange(
                "/v1/bookings/{id}/cancel",
                HttpMethod.POST,
                new HttpEntity<>(headers),
                Void.class,
                saved.getId()
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(bookingRepository.findById(saved.getId()).orElseThrow().getStatus())
                .isEqualTo(Booking.Status.CANCELLED);
        assertThat(tourSlotRepository.findById(slot.getId()).orElseThrow().getBookedCount()).isZero();
        var cancelledEvent = outboxEventRepository.findAll().stream()
                .filter(event -> "booking.cancelled".equals(event.getEventType()))
                .findFirst().orElseThrow();
        JsonNode payload = objectMapper.readTree(cancelledEvent.getPayload());
        assertThat(payload.path("refundPercentage").asInt()).isEqualTo(80);
        assertThat(payload.path("refundEligible").asBoolean()).isTrue();
    }

    private Booking createBooking(UUID customerId, Booking.Status status, Instant createdAt) {
        Booking booking = new Booking(
                customerId,
                slot.getTourId(),
                Booking.BookingType.GROUP,
                slot.getId(),
                slot.getDepartureDate(),
                slot.getEndDate(),
                2,
                Booking.PriceModel.PER_PERSON,
                TOTAL_AMOUNT.divide(BigDecimal.valueOf(2)),
                TOTAL_AMOUNT,
                "{}",
                "[{\"minimumDaysBeforeDeparture\":0,\"refundPercentage\":0}]",
                false,
                0
        );
        if (status != Booking.Status.PENDING) {
            booking.setStatus(status);
        }
        Booking saved = bookingRepository.saveAndFlush(booking);
        jdbcTemplate.update(
                "UPDATE bookings SET created_at = ?, updated_at = ? WHERE id = ?",
                createdAt.atOffset(ZoneOffset.UTC),
                createdAt.atOffset(ZoneOffset.UTC),
                saved.getId()
        );
        return saved;
    }

    private JsonNode getMyBookings(UUID customerId, int page, int size, HttpStatus expectedStatus)
            throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-User-Id", customerId.toString());
        ResponseEntity<String> response = restTemplate.exchange(
                "/v1/bookings/me?page={page}&size={size}",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class,
                page,
                size
        );
        assertThat(response.getStatusCode()).isEqualTo(expectedStatus);
        assertThat(response.getBody()).isNotBlank();
        return objectMapper.readTree(response.getBody());
    }
}
