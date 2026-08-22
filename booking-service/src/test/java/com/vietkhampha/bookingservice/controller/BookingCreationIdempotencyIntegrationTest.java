package com.vietkhampha.bookingservice.controller;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.vietkhampha.bookingservice.dto.CreateBookingResponse;
import com.vietkhampha.bookingservice.entity.TourSlot;
import com.vietkhampha.bookingservice.outbox.OutboxPoller;
import com.vietkhampha.bookingservice.repository.BookingParticipantRepository;
import com.vietkhampha.bookingservice.repository.BookingRepository;
import com.vietkhampha.bookingservice.repository.IdempotencyKeyRepository;
import com.vietkhampha.bookingservice.repository.OutboxEventRepository;
import com.vietkhampha.bookingservice.repository.TourSlotRepository;
import org.junit.jupiter.api.AfterAll;
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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "app.outbox.poller.enabled=false",
                "spring.kafka.listener.auto-startup=false",
                "spring.kafka.bootstrap-servers=localhost:1"
        }
)
@Testcontainers
class BookingCreationIdempotencyIntegrationTest {

    private static final String USER_HEADER = "X-User-Id";
    private static final String IDEMPOTENCY_HEADER = "Idempotency-Key";
    private static final BigDecimal BASE_PRICE = new BigDecimal("1000000");
    private static final AtomicLong TOUR_RESPONSE_DELAY_MILLIS = new AtomicLong();
    private static final HttpServer TOUR_SERVER = startTourServer();

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void tourServiceProperties(DynamicPropertyRegistry registry) {
        registry.add(
                "app.tour-service.base-url",
                () -> "http://127.0.0.1:" + TOUR_SERVER.getAddress().getPort()
        );
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private TourSlotRepository tourSlotRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private BookingParticipantRepository participantRepository;

    @Autowired
    private IdempotencyKeyRepository idempotencyKeyRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ApplicationContext applicationContext;

    @BeforeEach
    void clearEphemeralDatabase() {
        assertTrue(applicationContext.getBeansOfType(OutboxPoller.class).isEmpty());
        TOUR_RESPONSE_DELAY_MILLIS.set(0);
        jdbcTemplate.execute("""
                TRUNCATE TABLE
                    booking_participants,
                    idempotency_keys,
                    outbox_events,
                    processed_payment_events,
                    bookings,
                    tour_slots
                """);
    }

    @AfterAll
    static void stopTourServer() {
        TOUR_SERVER.stop(0);
    }

    @Test
    void firstRequestReturnsCreated_andSameRequestReplaysOriginalSnapshot() {
        UUID customerId = UUID.randomUUID();
        TourSlot slot = createSlot(10);
        Map<String, Object> request = request(slot.getId(), "itinerary-1", participant("Nguyen Van A", "0901000001", true));

        ResponseEntity<CreateBookingResponse> first = createBooking(customerId, "same-request-key", request);
        jdbcTemplate.update(
                "UPDATE bookings SET status = 'CONFIRMED', updated_at = ? WHERE id = ?",
                Instant.now().atOffset(ZoneOffset.UTC),
                requireBody(first).getBookingId()
        );
        ResponseEntity<CreateBookingResponse> replay = createBooking(customerId, "same-request-key", request);

        assertEquals(HttpStatus.CREATED, first.getStatusCode());
        assertEquals(HttpStatus.OK, replay.getStatusCode());
        assertSameSnapshot(requireBody(first), requireBody(replay));
        assertEquals(1, bookingRepository.count());
        assertEquals(1, participantRepository.count());
        assertEquals(1, idempotencyKeyRepository.count());
        assertEquals(1, outboxEventRepository.count());
        assertEquals(1, tourSlotRepository.findById(slot.getId()).orElseThrow().getBookedCount());

        Long ttlSeconds = jdbcTemplate.queryForObject("""
                SELECT extract(epoch FROM (expires_at - created_at))::bigint
                FROM idempotency_keys
                WHERE customer_id = ? AND key = ?
                """, Long.class, customerId, "same-request-key");
        assertEquals(Duration.ofHours(24).toSeconds(), ttlSeconds);

        Map<String, Object> storedIdempotencyData = jdbcTemplate.queryForMap("""
                SELECT request_hash, response_snapshot
                FROM idempotency_keys
                WHERE customer_id = ? AND key = ?
                """, customerId, "same-request-key");
        String requestHash = (String) storedIdempotencyData.get("request_hash");
        String responseSnapshot = (String) storedIdempotencyData.get("response_snapshot");
        assertTrue(requestHash.matches("[0-9a-f]{64}"));
        assertFalse(responseSnapshot.contains("Nguyen Van A"));
        assertFalse(responseSnapshot.contains("0901000001"));
        assertFalse(responseSnapshot.contains(customerId.toString()));
    }

    @Test
    void blankKeyIsRejectedBeforeAnyBookingWrite() {
        UUID customerId = UUID.randomUUID();
        TourSlot slot = createSlot(10);
        Map<String, Object> request = request(slot.getId(), null, participant("Nguyen Van A", null, true));

        ResponseEntity<Map> response = createBookingForError(customerId, "   ", request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("IDEMPOTENCY_KEY_INVALID", requireErrorBody(response).get("error"));
        assertEquals(0, bookingRepository.count());
        assertEquals(0, idempotencyKeyRepository.count());
        assertEquals(0, outboxEventRepository.count());
        assertEquals(0, tourSlotRepository.findById(slot.getId()).orElseThrow().getBookedCount());
    }

    @Test
    void sameKeyWithDifferentPayloadReturnsConflict_withoutChangingBooking() {
        UUID customerId = UUID.randomUUID();
        TourSlot slot = createSlot(10);
        Map<String, Object> firstRequest = request(
                slot.getId(),
                null,
                participant("Nguyen Van A", "0901000001", true)
        );
        Map<String, Object> changedRequest = request(
                slot.getId(),
                null,
                participant("Nguyen Van A", "0901999999", true)
        );

        assertEquals(HttpStatus.CREATED, createBooking(customerId, "reused-key", firstRequest).getStatusCode());
        ResponseEntity<Map> conflict = createBookingForError(customerId, "reused-key", changedRequest);

        assertEquals(HttpStatus.CONFLICT, conflict.getStatusCode());
        assertEquals("IDEMPOTENCY_KEY_REUSED", requireErrorBody(conflict).get("error"));
        assertEquals(1, bookingRepository.count());
        assertEquals(1, tourSlotRepository.findById(slot.getId()).orElseThrow().getBookedCount());
    }

    @Test
    void expiredCompletedKeyReturnsConflict_andIsNeverReusedAsANewRequest() {
        UUID customerId = UUID.randomUUID();
        TourSlot slot = createSlot(10);
        Map<String, Object> request = request(slot.getId(), null, participant("Nguyen Van A", null, true));

        assertEquals(HttpStatus.CREATED, createBooking(customerId, "expired-key", request).getStatusCode());
        Instant expiredAt = Instant.now().minus(Duration.ofMinutes(5));
        Instant createdAt = expiredAt.minus(Duration.ofHours(24));
        jdbcTemplate.update(
                "UPDATE idempotency_keys SET created_at = ?, expires_at = ? WHERE customer_id = ? AND key = ?",
                createdAt.atOffset(ZoneOffset.UTC),
                expiredAt.atOffset(ZoneOffset.UTC),
                customerId,
                "expired-key"
        );

        ResponseEntity<Map> conflict = createBookingForError(customerId, "expired-key", request);

        assertEquals(HttpStatus.CONFLICT, conflict.getStatusCode());
        assertEquals("IDEMPOTENCY_KEY_EXPIRED", requireErrorBody(conflict).get("error"));
        assertEquals(1, bookingRepository.count());
        assertEquals(1, tourSlotRepository.findById(slot.getId()).orElseThrow().getBookedCount());
    }

    @Test
    void legacyRecordAlwaysReturnsExpired_withoutSnapshotOrHash() {
        UUID customerId = UUID.randomUUID();
        TourSlot slot = createSlot(10);
        Map<String, Object> request = request(slot.getId(), null, participant("Nguyen Van A", null, true));
        CreateBookingResponse original = requireBody(createBooking(customerId, "source-key", request));

        jdbcTemplate.update("""
                INSERT INTO idempotency_keys (
                    customer_id, key, booking_id, record_state, request_hash, hash_version,
                    response_snapshot, created_at, expires_at
                ) VALUES (?, ?, ?, 'LEGACY_EXPIRED', NULL, NULL, NULL, ?, ?)
                """,
                customerId,
                "legacy-key",
                original.getBookingId(),
                Instant.now().minus(Duration.ofDays(2)).atOffset(ZoneOffset.UTC),
                Instant.now().minus(Duration.ofDays(1)).atOffset(ZoneOffset.UTC)
        );

        ResponseEntity<Map> conflict = createBookingForError(customerId, "legacy-key", request);

        assertEquals(HttpStatus.CONFLICT, conflict.getStatusCode());
        assertEquals("IDEMPOTENCY_KEY_EXPIRED", requireErrorBody(conflict).get("error"));
        Map<String, Object> legacyColumns = jdbcTemplate.queryForMap("""
                SELECT request_hash, hash_version, response_snapshot
                FROM idempotency_keys
                WHERE customer_id = ? AND key = ?
                """, customerId, "legacy-key");
        assertEquals(null, legacyColumns.get("request_hash"));
        assertEquals(null, legacyColumns.get("hash_version"));
        assertEquals(null, legacyColumns.get("response_snapshot"));
    }

    @Test
    void sameRawKeyForDifferentCustomersCreatesIndependentBookings() {
        UUID firstCustomer = UUID.randomUUID();
        UUID secondCustomer = UUID.randomUUID();
        TourSlot slot = createSlot(10);
        Map<String, Object> request = request(slot.getId(), null, participant("Nguyen Van A", null, true));

        ResponseEntity<CreateBookingResponse> first = createBooking(firstCustomer, "shared-raw-key", request);
        ResponseEntity<CreateBookingResponse> second = createBooking(secondCustomer, "shared-raw-key", request);

        assertEquals(HttpStatus.CREATED, first.getStatusCode());
        assertEquals(HttpStatus.CREATED, second.getStatusCode());
        assertNotEquals(requireBody(first).getBookingId(), requireBody(second).getBookingId());
        assertEquals(2, bookingRepository.count());
        assertEquals(2, idempotencyKeyRepository.count());
        assertEquals(2, tourSlotRepository.findById(slot.getId()).orElseThrow().getBookedCount());
    }

    @Test
    void concurrentSameKeyRequestsReturnOneCreatedAndOneReplay() throws Exception {
        UUID customerId = UUID.randomUUID();
        TourSlot slot = createSlot(10);
        Map<String, Object> request = request(slot.getId(), null, participant("Nguyen Van A", null, true));
        TOUR_RESPONSE_DELAY_MILLIS.set(300);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<ResponseEntity<CreateBookingResponse>> firstFuture = executor.submit(() -> {
                await(start);
                return createBooking(customerId, "concurrent-key", request);
            });
            Future<ResponseEntity<CreateBookingResponse>> secondFuture = executor.submit(() -> {
                await(start);
                return createBooking(customerId, "concurrent-key", request);
            });
            start.countDown();

            ResponseEntity<CreateBookingResponse> first = firstFuture.get(10, TimeUnit.SECONDS);
            ResponseEntity<CreateBookingResponse> second = secondFuture.get(10, TimeUnit.SECONDS);

            assertEquals(Set.of(HttpStatus.CREATED, HttpStatus.OK), Set.of(first.getStatusCode(), second.getStatusCode()));
            assertSameSnapshot(requireBody(first), requireBody(second));
            assertEquals(1, bookingRepository.count());
            assertEquals(1, participantRepository.count());
            assertEquals(1, idempotencyKeyRepository.count());
            assertEquals(1, outboxEventRepository.count());
            assertEquals(1, tourSlotRepository.findById(slot.getId()).orElseThrow().getBookedCount());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void failedBookingRollsBackIdempotencyClaimAndAllBookingWrites() {
        UUID customerId = UUID.randomUUID();
        TourSlot fullSlot = createSlot(1);
        fullSlot.reserve(1);
        tourSlotRepository.saveAndFlush(fullSlot);
        Map<String, Object> request = request(
                fullSlot.getId(),
                null,
                participant("Nguyen Van A", null, true)
        );

        ResponseEntity<Map> conflict = createBookingForError(customerId, "rollback-key", request);

        assertEquals(HttpStatus.CONFLICT, conflict.getStatusCode());
        assertEquals("SLOT_UNAVAILABLE", requireErrorBody(conflict).get("error"));
        assertEquals(0, idempotencyKeyRepository.count());
        assertEquals(0, bookingRepository.count());
        assertEquals(0, participantRepository.count());
        assertEquals(0, outboxEventRepository.count());
        assertEquals(1, tourSlotRepository.findById(fullSlot.getId()).orElseThrow().getBookedCount());
    }

    private TourSlot createSlot(int capacity) {
        return tourSlotRepository.saveAndFlush(
                new TourSlot("idempotency-tour-" + UUID.randomUUID(), LocalDate.now().plusDays(30), capacity)
        );
    }

    private ResponseEntity<CreateBookingResponse> createBooking(
            UUID customerId,
            String idempotencyKey,
            Map<String, Object> request
    ) {
        return restTemplate.exchange(
                "/v1/bookings",
                HttpMethod.POST,
                new HttpEntity<>(request, headers(customerId, idempotencyKey)),
                CreateBookingResponse.class
        );
    }

    private ResponseEntity<Map> createBookingForError(
            UUID customerId,
            String idempotencyKey,
            Map<String, Object> request
    ) {
        return restTemplate.exchange(
                "/v1/bookings",
                HttpMethod.POST,
                new HttpEntity<>(request, headers(customerId, idempotencyKey)),
                Map.class
        );
    }

    private HttpHeaders headers(UUID customerId, String idempotencyKey) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(USER_HEADER, customerId.toString());
        headers.set(IDEMPOTENCY_HEADER, idempotencyKey);
        return headers;
    }

    private Map<String, Object> request(
            UUID slotId,
            String generatedItineraryId,
            Map<String, Object>... participants
    ) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("tourSlotId", slotId);
        request.put("participants", new ArrayList<>(List.of(participants)));
        request.put("generatedItineraryId", generatedItineraryId);
        return request;
    }

    private Map<String, Object> participant(String fullName, String phone, boolean primaryContact) {
        Map<String, Object> participant = new LinkedHashMap<>();
        participant.put("fullName", fullName);
        participant.put("phone", phone);
        participant.put("primaryContact", primaryContact);
        return participant;
    }

    private CreateBookingResponse requireBody(ResponseEntity<CreateBookingResponse> response) {
        assertNotNull(response.getBody());
        return response.getBody();
    }

    private Map requireErrorBody(ResponseEntity<Map> response) {
        assertNotNull(response.getBody());
        return response.getBody();
    }

    private void assertSameSnapshot(CreateBookingResponse expected, CreateBookingResponse actual) {
        assertEquals(expected.getBookingId(), actual.getBookingId());
        assertEquals(expected.getStatus(), actual.getStatus());
        assertEquals(expected.getTotalAmount(), actual.getTotalAmount());
        assertEquals(expected.getHoldExpiresAt(), actual.getHoldExpiresAt());
    }

    private static HttpServer startTourServer() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/v1/tours/", BookingCreationIdempotencyIntegrationTest::respondWithTour);
            server.start();
            return server;
        } catch (IOException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private static void respondWithTour(HttpExchange exchange) throws IOException {
        try {
            long delayMillis = TOUR_RESPONSE_DELAY_MILLIS.get();
            if (delayMillis > 0) {
                Thread.sleep(delayMillis);
            }
            byte[] response = ("{\"basePrice\":" + BASE_PRICE.toPlainString() + "}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            exchange.sendResponseHeaders(503, -1);
        } finally {
            exchange.close();
        }
    }

    private void await(CountDownLatch latch) {
        try {
            assertTrue(latch.await(5, TimeUnit.SECONDS));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting to start concurrent request", exception);
        }
    }
}
