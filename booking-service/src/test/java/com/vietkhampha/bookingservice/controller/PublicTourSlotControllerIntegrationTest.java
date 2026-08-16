package com.vietkhampha.bookingservice.controller;

import com.vietkhampha.bookingservice.dto.PublicTourSlotResponse;
import com.vietkhampha.bookingservice.entity.TourSlot;
import com.vietkhampha.bookingservice.repository.TourSlotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "app.outbox.poller.enabled=false",
                "spring.kafka.listener.auto-startup=false"
        }
)
@Testcontainers
class PublicTourSlotControllerIntegrationTest {

    private static final String TOUR_ID = "public-slot-test-tour";
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private TourSlotRepository tourSlotRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void deleteSlots() {
        tourSlotRepository.deleteAll();
    }

    @Test
    void getTourSlots_returnsFutureAvailableSlotsInDepartureDateOrder() {
        LocalDate firstDepartureDate = LocalDate.now(BUSINESS_ZONE).plusDays(10);
        LocalDate secondDepartureDate = LocalDate.now(BUSINESS_ZONE).plusDays(20);
        tourSlotRepository.saveAndFlush(new TourSlot(TOUR_ID, secondDepartureDate, 5));

        TourSlot firstSlot = new TourSlot(TOUR_ID, firstDepartureDate, 5);
        firstSlot.reserve(2);
        TourSlot savedFirstSlot = tourSlotRepository.saveAndFlush(firstSlot);

        ResponseEntity<PublicTourSlotResponse[]> response = getTourSlots(TOUR_ID);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        PublicTourSlotResponse[] slots = requireBody(response);
        assertEquals(2, slots.length);
        assertEquals(savedFirstSlot.getId(), slots[0].slotId());
        assertEquals(firstDepartureDate, slots[0].departureDate());
        assertEquals(3, slots[0].availableSlots());
        assertTrue(slots[0].bookable());
        assertEquals(secondDepartureDate, slots[1].departureDate());
    }

    @Test
    void getTourSlots_returnsFullSlotAsNotBookable() {
        TourSlot slot = new TourSlot(TOUR_ID, LocalDate.now(BUSINESS_ZONE).plusDays(10), 2);
        slot.reserve(2);
        tourSlotRepository.saveAndFlush(slot);

        PublicTourSlotResponse[] slots = requireBody(getTourSlots(TOUR_ID));

        assertEquals(1, slots.length);
        assertEquals(0, slots[0].availableSlots());
        assertFalse(slots[0].bookable());
    }

    @Test
    void getTourSlots_excludesClosedSlot() {
        TourSlot slot = tourSlotRepository.saveAndFlush(
                new TourSlot(TOUR_ID, LocalDate.now(BUSINESS_ZONE).plusDays(10), 5)
        );
        jdbcTemplate.update(
                "UPDATE tour_slots SET status = 'CLOSED' WHERE id = ?",
                slot.getId()
        );

        PublicTourSlotResponse[] slots = requireBody(getTourSlots(TOUR_ID));

        assertEquals(0, slots.length);
    }

    @Test
    void getTourSlots_excludesPastAndCurrentDateSlots() {
        tourSlotRepository.saveAndFlush(
                new TourSlot(TOUR_ID, LocalDate.now(BUSINESS_ZONE).minusDays(1), 5)
        );
        tourSlotRepository.saveAndFlush(
                new TourSlot(TOUR_ID, LocalDate.now(BUSINESS_ZONE), 5)
        );

        PublicTourSlotResponse[] slots = requireBody(getTourSlots(TOUR_ID));

        assertEquals(0, slots.length);
    }

    @Test
    void getTourSlots_returnsEmptyListWhenTourHasNoSlots() {
        tourSlotRepository.saveAndFlush(
                new TourSlot("another-tour", LocalDate.now(BUSINESS_ZONE).plusDays(10), 5)
        );

        ResponseEntity<PublicTourSlotResponse[]> response = getTourSlots("tour-without-slots");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(0, requireBody(response).length);
    }

    private ResponseEntity<PublicTourSlotResponse[]> getTourSlots(String tourId) {
        return restTemplate.getForEntity(
                "/v1/tours/{tourId}/slots",
                PublicTourSlotResponse[].class,
                tourId
        );
    }

    private PublicTourSlotResponse[] requireBody(ResponseEntity<PublicTourSlotResponse[]> response) {
        PublicTourSlotResponse[] body = response.getBody();
        if (body == null) {
            throw new AssertionError("Response body must not be null");
        }
        return body;
    }
}
