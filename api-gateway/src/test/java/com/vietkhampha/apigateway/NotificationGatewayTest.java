package com.vietkhampha.apigateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NotificationGatewayTest {

    @Test
    void notificationRouteTargetsNotificationService() throws IOException {
        List<PropertySource<?>> propertySources = new YamlPropertySourceLoader()
                .load("gateway-configuration", new ClassPathResource("application.yml"));
        PropertySource<?> properties = propertySources.get(0);
        int routeIndex = findRouteIndex(properties, "notification-service");

        assertEquals("http://notification-service:8080",
                properties.getProperty(routeProperty(routeIndex, "uri")));
        assertEquals("Path=/v1/notifications/**",
                properties.getProperty(routeProperty(routeIndex, "predicates[0]")));
    }

    private int findRouteIndex(PropertySource<?> properties, String routeId) {
        for (int index = 0; index < 50; index++) {
            if (routeId.equals(properties.getProperty(routeProperty(index, "id")))) return index;
        }
        throw new AssertionError("Route not found: " + routeId);
    }

    private String routeProperty(int routeIndex, String property) {
        return "spring.cloud.gateway.routes[" + routeIndex + "]." + property;
    }
}
