package com.vietkhampha.paymentservice.client;

import com.vietkhampha.paymentservice.exception.BusinessException;
import com.vietkhampha.paymentservice.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Component
public class BookingServiceClient {

    private final RestClient restClient;

    public BookingServiceClient(@Value("${app.booking-service.base-url}") String baseUrl) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
    }

    public record BookingInfo(UUID bookingId, String status, BigDecimal totalAmount, Instant holdExpiresAt) {}

    public BookingInfo getBooking(UUID bookingId, String userIdHeader) {
        Map<String, Object> response;
        try {
            response = restClient.get()
                    .uri("/v1/bookings/{id}", bookingId)
                    .header("X-User-Id", userIdHeader)
                    .retrieve()
                    .body(Map.class);
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == 404) {
                throw new BusinessException(ErrorCode.BOOKING_NOT_FOUND);
            }
            throw exception;
        }

        if (response == null) {
            throw new IllegalStateException("Khong lay duoc thong tin booking tu Booking Service");
        }

        return new BookingInfo(
                UUID.fromString((String) response.get("bookingId")),
                (String) response.get("status"),
                new BigDecimal(response.get("totalAmount").toString()),
                Instant.parse((String) response.get("holdExpiresAt"))
        );
    }
}
