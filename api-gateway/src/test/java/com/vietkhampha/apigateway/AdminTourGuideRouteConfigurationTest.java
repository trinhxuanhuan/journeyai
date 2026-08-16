package com.vietkhampha.apigateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdminTourGuideRouteConfigurationTest {

    @Test
    void adminTourGuideRoute_targetsTourServiceBeforeAdminFallback() throws IOException {
        List<PropertySource<?>> propertySources = new YamlPropertySourceLoader()
                .load("gateway-configuration", new ClassPathResource("application.yml"));
        PropertySource<?> properties = propertySources.get(0);

        int guideRouteIndex = findRouteIndex(properties, "tour-service-admin-tour-guides");
        int fallbackRouteIndex = findRouteIndex(properties, "auth-service-admin-demo");

        assertEquals(
                "http://tour-service:8080",
                properties.getProperty(routeProperty(guideRouteIndex, "uri"))
        );
        assertEquals(
                "Path=/v1/admin/tour-guides/**",
                properties.getProperty(routeProperty(guideRouteIndex, "predicates[0]"))
        );
        assertTrue(
                routeOrder(properties, guideRouteIndex) < routeOrder(properties, fallbackRouteIndex),
                "Tour guide route must be evaluated before the generic admin fallback"
        );
    }

    private int findRouteIndex(PropertySource<?> properties, String routeId) {
        for (int index = 0; index < 50; index++) {
            if (routeId.equals(properties.getProperty(routeProperty(index, "id")))) {
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
