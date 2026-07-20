package com.vietkhampha.bookingservice.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.Map;

@Component
public class TourServiceClient {

    private final RestClient restClient;

    public TourServiceClient(@Value("${app.tour-service.base-url}") String baseUrl) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
    }

    public BigDecimal getTourBasePrice(String tourId) {
        Map<String, Object> response = restClient.get()
                .uri("/v1/tours/{id}", tourId)
                .retrieve()
                .body(Map.class);

        if (response == null || response.get("basePrice") == null) {
            throw new IllegalStateException("Khong lay duoc basePrice tu Tour Service cho tourId=" + tourId);
        }
        return new BigDecimal(response.get("basePrice").toString());
    }
}
