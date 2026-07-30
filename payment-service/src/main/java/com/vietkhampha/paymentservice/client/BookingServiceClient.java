package com.vietkhampha.paymentservice.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@Component
public class BookingServiceClient {

    private final RestClient restClient;

    public BookingServiceClient(@Value("${app.booking-service.base-url}") String baseUrl) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
    }

    public record BookingInfo(UUID bookingId, String status, BigDecimal totalAmount) {}
    public BookingInfo getBooking(UUID bookingId, String userIdHeader) {
        Map<String, Object> response = restClient.get()
                .uri("/v1/bookings/{id}", bookingId)
                .header("X-User-Id", userIdHeader)
                .retrieve()
                .body(Map.class);

        if (response == null) {
            throw new IllegalStateException("Khong lay duoc thong tin booking tu Booking Service");
        }

        return new BookingInfo(
                UUID.fromString((String) response.get("bookingId")),
                (String) response.get("status"),
                new BigDecimal(response.get("totalAmount").toString())
        );
    }
}