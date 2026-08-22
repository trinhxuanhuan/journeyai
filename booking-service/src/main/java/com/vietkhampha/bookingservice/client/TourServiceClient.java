package com.vietkhampha.bookingservice.client;

import com.vietkhampha.bookingservice.exception.BusinessException;
import com.vietkhampha.bookingservice.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.ResourceAccessException;

import java.math.BigDecimal;
import java.util.Map;

@Component
public class TourServiceClient {

    private final RestClient restClient;

    public TourServiceClient(@Value("${app.tour-service.base-url}") String baseUrl) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
    }

    public void requireActiveTour(String tourId) {
        Map<String, Object> response;
        try {
            response = restClient.get()
                    .uri("/v1/tours/{id}", tourId)
                    .retrieve()
                    .body(Map.class);
        } catch (HttpClientErrorException.NotFound exception) {
            throw new BusinessException(ErrorCode.TOUR_NOT_AVAILABLE);
        } catch (HttpServerErrorException | ResourceAccessException exception) {
            throw new BusinessException(ErrorCode.TOUR_SERVICE_UNAVAILABLE);
        } catch (RestClientResponseException exception) {
            throw new BusinessException(ErrorCode.TOUR_SERVICE_INVALID_RESPONSE);
        }

        if (response == null
                || !tourId.equals(response.get("id"))
                || response.get("status") == null) {
            throw new BusinessException(ErrorCode.TOUR_SERVICE_INVALID_RESPONSE);
        }
        if (!"ACTIVE".equals(response.get("status").toString())) {
            throw new BusinessException(ErrorCode.TOUR_NOT_AVAILABLE);
        }
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
