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
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class TourServiceClient {

    private final RestClient restClient;

    public TourServiceClient(@Value("${app.tour-service.base-url}") String baseUrl) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
    }

    public record CancellationRuleInfo(int minimumDaysBeforeDeparture, int refundPercentage) {}

    public record TourInfo(
            String id,
            String name,
            String status,
            String tourType,
            String priceModel,
            BigDecimal basePrice,
            int minGroupSize,
            int maxGroupSize,
            String guideMode,
            BigDecimal optionalGuidePrice,
            int durationDays,
            BigDecimal childPricePercentage,
            BigDecimal singleRoomSupplement,
            List<CancellationRuleInfo> cancellationPolicy,
            Map<String, Object> commercialData,
            String legacyGuideId
    ) {}

    public TourInfo requireActiveTour(String tourId) {
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
        return toTourInfo(response);
    }

    public BigDecimal getTourBasePrice(String tourId) {
        return requireActiveTour(tourId).basePrice();
    }

    public void requireActiveGuide(String guideId) {
        Map<String, Object> response;
        try {
            response = restClient.get()
                    .uri("/v1/admin/tour-guides/{id}", guideId)
                    .retrieve()
                    .body(Map.class);
        } catch (HttpClientErrorException.NotFound exception) {
            throw new BusinessException(ErrorCode.GUIDE_NOT_AVAILABLE);
        } catch (HttpServerErrorException | ResourceAccessException exception) {
            throw new BusinessException(ErrorCode.TOUR_SERVICE_UNAVAILABLE);
        } catch (RestClientResponseException exception) {
            throw new BusinessException(ErrorCode.TOUR_SERVICE_INVALID_RESPONSE);
        }

        if (response == null
                || !guideId.equals(response.get("id"))
                || !Boolean.TRUE.equals(response.get("active"))) {
            throw new BusinessException(ErrorCode.GUIDE_NOT_AVAILABLE);
        }
    }

    @SuppressWarnings("unchecked")
    private TourInfo toTourInfo(Map<String, Object> response) {
        try {
            List<CancellationRuleInfo> cancellationRules = new ArrayList<>();
            Object rawRules = response.get("cancellationPolicy");
            if (rawRules instanceof List<?> rules) {
                for (Object rawRule : rules) {
                    if (rawRule instanceof Map<?, ?> rule) {
                        cancellationRules.add(new CancellationRuleInfo(
                                number(rule.get("minimumDaysBeforeDeparture"), 0).intValue(),
                                number(rule.get("refundPercentage"), 0).intValue()
                        ));
                    }
                }
            }
            if (cancellationRules.isEmpty()) {
                cancellationRules = List.of(
                        new CancellationRuleInfo(7, 100),
                        new CancellationRuleInfo(3, 50),
                        new CancellationRuleInfo(0, 0)
                );
            }

            BigDecimal childPercentage = BigDecimal.valueOf(75);
            if (response.get("childPolicy") instanceof Map<?, ?> childPolicy
                    && childPolicy.get("pricePercentage") != null) {
                childPercentage = decimal(childPolicy.get("pricePercentage"), childPercentage);
            }

            return new TourInfo(
                    response.get("id").toString(),
                    string(response.get("name"), ""),
                    response.get("status").toString(),
                    string(response.get("tourType"), "GROUP"),
                    string(response.get("priceModel"), "PER_PERSON"),
                    decimal(response.get("basePrice"), null),
                    number(response.get("minGroupSize"), 1).intValue(),
                    number(response.get("maxGroupSize"), 30).intValue(),
                    string(response.get("guideMode"), "INCLUDED"),
                    decimal(response.get("optionalGuidePrice"), BigDecimal.ZERO),
                    number(response.get("durationDays"), 1).intValue(),
                    childPercentage,
                    decimal(response.get("singleRoomSupplement"), BigDecimal.ZERO),
                    List.copyOf(cancellationRules),
                    Collections.unmodifiableMap(new LinkedHashMap<>(response)),
                    string(response.get("tourGuideId"), null)
            );
        } catch (RuntimeException exception) {
            throw new BusinessException(ErrorCode.TOUR_SERVICE_INVALID_RESPONSE);
        }
    }

    private Number number(Object value, Number fallback) {
        return value instanceof Number number ? number : fallback;
    }

    private BigDecimal decimal(Object value, BigDecimal fallback) {
        return value == null ? fallback : new BigDecimal(value.toString());
    }

    private String string(Object value, String fallback) {
        return value == null ? fallback : value.toString();
    }
}
