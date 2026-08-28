package com.vietkhampha.apigateway;

import com.vietkhampha.apigateway.security.JwtAuthenticationFilter;
import com.vietkhampha.apigateway.security.JwtValidator;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class AiItineraryGatewayTest {

    @Test
    void aiRouteTargetsAiService() throws IOException {
        List<PropertySource<?>> propertySources = new YamlPropertySourceLoader()
                .load("gateway-configuration", new ClassPathResource("application.yml"));
        PropertySource<?> properties = propertySources.get(0);
        int routeIndex = findRouteIndex(properties, "ai-service");

        assertEquals(
                "http://ai-service:8000",
                properties.getProperty(routeProperty(routeIndex, "uri"))
        );
        assertEquals(
                "Path=/v1/ai/**",
                properties.getProperty(routeProperty(routeIndex, "predicates[0]"))
        );
    }

    @Test
    void pingAndSharedItineraryArePublic() {
        JwtValidator jwtValidator = mock(JwtValidator.class);
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtValidator);

        assertPublic(filter, "/v1/ai/ping");
        assertPublic(filter, "/v1/ai/shared/share-token");
        verifyNoInteractions(jwtValidator);
    }

    @Test
    void personalItineraryEndpointsRequireAuthentication() {
        JwtValidator jwtValidator = mock(JwtValidator.class);
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtValidator);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/v1/ai/itineraries").build()
        );
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.filter(exchange, received -> {
            chainCalled.set(true);
            return Mono.empty();
        }).block(Duration.ofSeconds(1));

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        assertFalse(chainCalled.get());
        verifyNoInteractions(jwtValidator);
    }

    private void assertPublic(JwtAuthenticationFilter filter, String path) {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get(path).build()
        );
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.filter(exchange, received -> {
            chainCalled.set(true);
            return Mono.empty();
        }).block(Duration.ofSeconds(1));

        assertEquals(null, exchange.getResponse().getStatusCode());
        assertTrue(chainCalled.get());
    }

    private int findRouteIndex(PropertySource<?> properties, String routeId) {
        for (int index = 0; index < 50; index++) {
            if (routeId.equals(properties.getProperty(routeProperty(index, "id")))) {
                return index;
            }
        }
        throw new AssertionError("Route not found: " + routeId);
    }

    private String routeProperty(int routeIndex, String property) {
        return "spring.cloud.gateway.routes[" + routeIndex + "]." + property;
    }
}
