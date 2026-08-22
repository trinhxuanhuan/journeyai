package com.vietkhampha.bookingservice.controller;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.vietkhampha.bookingservice.outbox.OutboxPoller;
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
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
class AdminSlotControllerIntegrationTest {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
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
    private ApplicationContext applicationContext;

    @BeforeEach
    void clearSlots() {
        assertTrue(applicationContext.getBeansOfType(OutboxPoller.class).isEmpty());
        tourSlotRepository.deleteAll();
    }

    @AfterAll
    static void stopTourServer() {
        TOUR_SERVER.stop(0);
    }

    @Test
    void activeTour_createsOpenSlot() {
        LocalDate departureDate = futureDate();

        ResponseEntity<Map> response = createSlot("active-tour", departureDate, 20);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        Map body = requireBody(response);
        assertNotNull(body.get("id"));
        assertEquals("active-tour", body.get("tourId"));
        assertEquals(departureDate.toString(), body.get("departureDate"));
        assertEquals(20, body.get("maxCapacity"));
        assertEquals(0, body.get("bookedCount"));
        assertEquals("OPEN", body.get("status"));
        assertEquals(1, tourSlotRepository.count());
    }

    @Test
    void missingTour_returnsNotFoundWithoutCreatingSlot() {
        ResponseEntity<Map> response = createSlot("missing-tour", futureDate(), 20);

        assertBusinessError(response, HttpStatus.NOT_FOUND, "TOUR_NOT_AVAILABLE");
        assertEquals(0, tourSlotRepository.count());
    }

    @Test
    void nonActiveTourResponse_returnsNotFoundWithoutCreatingSlot() {
        ResponseEntity<Map> response = createSlot("inactive-tour", futureDate(), 20);

        assertBusinessError(response, HttpStatus.NOT_FOUND, "TOUR_NOT_AVAILABLE");
        assertEquals(0, tourSlotRepository.count());
    }

    @Test
    void unavailableTourService_returnsServiceUnavailableWithoutCreatingSlot() {
        ResponseEntity<Map> response = createSlot("upstream-error-tour", futureDate(), 20);

        assertBusinessError(response, HttpStatus.SERVICE_UNAVAILABLE, "TOUR_SERVICE_UNAVAILABLE");
        assertEquals(0, tourSlotRepository.count());
    }

    @Test
    void mismatchedTourResponse_returnsBadGatewayWithoutCreatingSlot() {
        ResponseEntity<Map> response = createSlot("mismatched-tour", futureDate(), 20);

        assertBusinessError(response, HttpStatus.BAD_GATEWAY, "TOUR_SERVICE_INVALID_RESPONSE");
        assertEquals(0, tourSlotRepository.count());
    }

    @Test
    void repeatedTourAndDepartureDate_returnsConflictWithoutCreatingDuplicate() {
        LocalDate departureDate = futureDate();

        ResponseEntity<Map> first = createSlot("active-tour", departureDate, 20);
        ResponseEntity<Map> duplicate = createSlot("active-tour", departureDate, 30);

        assertEquals(HttpStatus.CREATED, first.getStatusCode());
        assertBusinessError(duplicate, HttpStatus.CONFLICT, "SLOT_ALREADY_EXISTS");
        assertEquals(1, tourSlotRepository.count());
        assertEquals(20, tourSlotRepository.findAll().get(0).getMaxCapacity());
    }

    @Test
    void concurrentDuplicateRequests_returnOneCreatedAndOneConflict() throws Exception {
        LocalDate departureDate = futureDate();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<ResponseEntity<Map>> first = executor.submit(() -> {
                await(start);
                return createSlot("active-tour", departureDate, 20);
            });
            Future<ResponseEntity<Map>> second = executor.submit(() -> {
                await(start);
                return createSlot("active-tour", departureDate, 20);
            });
            start.countDown();

            ResponseEntity<Map> firstResponse = first.get(10, TimeUnit.SECONDS);
            ResponseEntity<Map> secondResponse = second.get(10, TimeUnit.SECONDS);

            assertEquals(
                    Set.of(HttpStatus.CREATED, HttpStatus.CONFLICT),
                    Set.of(firstResponse.getStatusCode(), secondResponse.getStatusCode())
            );
            ResponseEntity<Map> conflict = firstResponse.getStatusCode() == HttpStatus.CONFLICT
                    ? firstResponse
                    : secondResponse;
            assertEquals("SLOT_ALREADY_EXISTS", requireBody(conflict).get("error"));
            assertEquals(1, tourSlotRepository.count());
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    private ResponseEntity<Map> createSlot(String tourId, LocalDate departureDate, int maxCapacity) {
        return restTemplate.exchange(
                "/v1/admin/tours/{tourId}/slots",
                HttpMethod.POST,
                new HttpEntity<>(Map.of(
                        "departureDate", departureDate.toString(),
                        "maxCapacity", maxCapacity
                )),
                Map.class,
                tourId
        );
    }

    private void assertBusinessError(ResponseEntity<Map> response, HttpStatus status, String errorCode) {
        assertEquals(status, response.getStatusCode());
        assertEquals(errorCode, requireBody(response).get("error"));
    }

    private Map requireBody(ResponseEntity<Map> response) {
        assertNotNull(response.getBody());
        return response.getBody();
    }

    private LocalDate futureDate() {
        return LocalDate.now(BUSINESS_ZONE).plusDays(30);
    }

    private static HttpServer startTourServer() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/v1/tours/", AdminSlotControllerIntegrationTest::respondWithTour);
            server.start();
            return server;
        } catch (IOException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private static void respondWithTour(HttpExchange exchange) throws IOException {
        try {
            String tourId = exchange.getRequestURI().getPath().substring("/v1/tours/".length());
            switch (tourId) {
                case "active-tour" -> sendJson(
                        exchange,
                        200,
                        "{\"id\":\"active-tour\",\"status\":\"ACTIVE\"}"
                );
                case "inactive-tour" -> sendJson(
                        exchange,
                        200,
                        "{\"id\":\"inactive-tour\",\"status\":\"INACTIVE\"}"
                );
                case "mismatched-tour" -> sendJson(
                        exchange,
                        200,
                        "{\"id\":\"another-tour\",\"status\":\"ACTIVE\"}"
                );
                case "upstream-error-tour" -> sendJson(
                        exchange,
                        503,
                        "{\"error\":\"TEMPORARILY_UNAVAILABLE\"}"
                );
                default -> sendJson(exchange, 404, "{\"error\":\"TOUR_NOT_FOUND\"}");
            }
        } finally {
            exchange.close();
        }
    }

    private static void sendJson(HttpExchange exchange, int status, String responseBody) throws IOException {
        byte[] response = responseBody.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", MediaType.APPLICATION_JSON_VALUE);
        exchange.sendResponseHeaders(status, response.length);
        exchange.getResponseBody().write(response);
    }

    private void await(CountDownLatch latch) {
        try {
            assertTrue(latch.await(5, TimeUnit.SECONDS));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting to start concurrent slot requests", exception);
        }
    }
}
