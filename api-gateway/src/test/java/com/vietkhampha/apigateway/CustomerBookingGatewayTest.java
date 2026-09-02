package com.vietkhampha.apigateway;

import com.vietkhampha.apigateway.security.JwtAuthenticationFilter;
import com.vietkhampha.apigateway.security.JwtValidator;
import com.vietkhampha.apigateway.security.TokenRevocationChecker;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CustomerBookingGatewayTest {

    @Test
    void customerBookingRouteTargetsBookingService() throws IOException {
        List<PropertySource<?>> propertySources = new YamlPropertySourceLoader()
                .load("gateway-configuration", new ClassPathResource("application.yml"));
        PropertySource<?> properties = propertySources.get(0);
        int routeIndex = findRouteIndex(properties, "booking-service");

        assertEquals(
                "http://booking-service:8080",
                properties.getProperty(routeProperty(routeIndex, "uri"))
        );
        assertEquals(
                "Path=/v1/bookings/**",
                properties.getProperty(routeProperty(routeIndex, "predicates[0]"))
        );
    }

    @Test
    void requestWithoutJwtIsRejectedBeforeRouting() {
        JwtValidator jwtValidator = mock(JwtValidator.class);
        TokenRevocationChecker tokenRevocationChecker = mock(TokenRevocationChecker.class);
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtValidator, tokenRevocationChecker);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/v1/bookings/me").build()
        );
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.filter(exchange, received -> {
            chainCalled.set(true);
            return Mono.empty();
        }).block(Duration.ofSeconds(1));

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        assertFalse(chainCalled.get());
        verifyNoInteractions(jwtValidator, tokenRevocationChecker);
    }

    @Test
    void authenticatedCustomerIsForwardedWithTrustedIdentityReplacingSpoofedHeaders() {
        String customerId = UUID.randomUUID().toString();
        JwtValidator jwtValidator = mock(JwtValidator.class);
        TokenRevocationChecker tokenRevocationChecker = mock(TokenRevocationChecker.class);
        Claims claims = mock(Claims.class);
        when(claims.getSubject()).thenReturn(customerId);
        when(claims.get("role", String.class)).thenReturn("CUSTOMER");
        when(jwtValidator.validateAndParse("customer-token")).thenReturn(claims);
        when(tokenRevocationChecker.isRevoked("customer-token")).thenReturn(Mono.just(false));
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtValidator, tokenRevocationChecker);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/v1/bookings/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer customer-token")
                        .header("X-User-Id", UUID.randomUUID().toString())
                        .header("X-User-Role", "ADMIN")
                        .build()
        );
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();
        GatewayFilterChain chain = received -> {
            forwarded.set(received);
            return Mono.empty();
        };

        filter.filter(exchange, chain).block(Duration.ofSeconds(1));

        assertEquals(null, exchange.getResponse().getStatusCode());
        assertEquals(List.of(customerId), forwarded.get().getRequest().getHeaders().get("X-User-Id"));
        assertEquals(List.of("CUSTOMER"), forwarded.get().getRequest().getHeaders().get("X-User-Role"));
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
