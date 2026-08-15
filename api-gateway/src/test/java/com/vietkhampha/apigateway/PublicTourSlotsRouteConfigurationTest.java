package com.vietkhampha.apigateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PublicTourSlotsRouteConfigurationTest {

    @Test
    void publicTourSlotsRoute_targetsBookingServiceWithHigherPriorityThanTourService() throws IOException {
        List<PropertySource<?>> propertySources = new YamlPropertySourceLoader()
                .load("gateway-configuration", new ClassPathResource("application.yml"));
        PropertySource<?> properties = propertySources.get(0);

        int publicSlotsRouteIndex = findRouteIndex(properties, "booking-service-public-tour-slots");
        int tourServiceRouteIndex = findRouteIndex(properties, "tour-service");

        assertEquals(
                "http://booking-service:8080",
                properties.getProperty(routeProperty(publicSlotsRouteIndex, "uri"))
        );
        assertEquals(
                -1,
                properties.getProperty(routeProperty(publicSlotsRouteIndex, "order"))
        );
        assertEquals(
                "Path=/v1/tours/*/slots",
                properties.getProperty(routeProperty(publicSlotsRouteIndex, "predicates[0]"))
        );
        assertTrue(
                routeOrder(properties, publicSlotsRouteIndex) < routeOrder(properties, tourServiceRouteIndex),
                "Public slot route must be evaluated before the generic tour route"
        );
    }

    private int findRouteIndex(PropertySource<?> properties, String routeId) {
        for (int index = 0; index < 50; index++) {
            Object configuredRouteId = properties.getProperty(routeProperty(index, "id"));
            if (routeId.equals(configuredRouteId)) {
                return index;
            }
        }
        throw new AssertionError("Route not found: " + routeId);
    }

    private int routeOrder(PropertySource<?> properties, int routeIndex) {
        Object configuredOrder = properties.getProperty(routeProperty(routeIndex, "order"));
        return configuredOrder instanceof Number number ? number.intValue() : 0;
    }

    private String routeProperty(int routeIndex, String property) {
        return "spring.cloud.gateway.routes[" + routeIndex + "]." + property;
    }
}
