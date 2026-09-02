package com.vietkhampha.apigateway;

import com.vietkhampha.apigateway.security.JwtAuthenticationFilter;
import com.vietkhampha.apigateway.security.JwtValidator;
import com.vietkhampha.apigateway.security.TokenRevocationChecker;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AdminTourGuideGatewaySecurityTest {

    private JwtValidator jwtValidator;
    private TokenRevocationChecker tokenRevocationChecker;
    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        jwtValidator = mock(JwtValidator.class);
        tokenRevocationChecker = mock(TokenRevocationChecker.class);
        filter = new JwtAuthenticationFilter(jwtValidator, tokenRevocationChecker);
    }

    @Test
    void requestWithoutJwt_isRejectedWithUnauthorized() {
        MockServerWebExchange exchange = exchangeWithoutToken();
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.filter(exchange, trackingChain(chainCalled)).block(Duration.ofSeconds(1));

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        assertFalse(chainCalled.get());
        verifyNoInteractions(jwtValidator, tokenRevocationChecker);
    }

    @Test
    void nonAdminJwt_isRejectedWithForbidden() {
        Claims claims = claims("CUSTOMER");
        when(jwtValidator.validateAndParse("customer-token")).thenReturn(claims);
        when(tokenRevocationChecker.isRevoked("customer-token")).thenReturn(Mono.just(false));
        MockServerWebExchange exchange = exchangeWithToken("customer-token");
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.filter(exchange, trackingChain(chainCalled)).block(Duration.ofSeconds(1));

        assertEquals(HttpStatus.FORBIDDEN, exchange.getResponse().getStatusCode());
        assertFalse(chainCalled.get());
    }

    @Test
    void adminJwt_isForwardedWithTrustedUserHeaders() {
        String adminId = UUID.randomUUID().toString();
        Claims claims = mock(Claims.class);
        when(claims.getSubject()).thenReturn(adminId);
        when(claims.get("role", String.class)).thenReturn("ADMIN");
        when(jwtValidator.validateAndParse("admin-token")).thenReturn(claims);
        when(tokenRevocationChecker.isRevoked("admin-token")).thenReturn(Mono.just(false));
        MockServerWebExchange exchange = exchangeWithToken("admin-token");
        AtomicReference<ServerWebExchange> forwardedExchange = new AtomicReference<>();
        GatewayFilterChain chain = received -> {
            forwardedExchange.set(received);
            return Mono.empty();
        };

        filter.filter(exchange, chain).block(Duration.ofSeconds(1));

        assertEquals(null, exchange.getResponse().getStatusCode());
        assertEquals(adminId, forwardedExchange.get().getRequest().getHeaders().getFirst("X-User-Id"));
        assertEquals("ADMIN", forwardedExchange.get().getRequest().getHeaders().getFirst("X-User-Role"));
    }

    private Claims claims(String role) {
        Claims claims = mock(Claims.class);
        when(claims.getSubject()).thenReturn(UUID.randomUUID().toString());
        when(claims.get("role", String.class)).thenReturn(role);
        return claims;
    }

    private MockServerWebExchange exchangeWithoutToken() {
        return MockServerWebExchange.from(
                MockServerHttpRequest.post("/v1/admin/tour-guides").build()
        );
    }

    private MockServerWebExchange exchangeWithToken(String token) {
        return MockServerWebExchange.from(
                MockServerHttpRequest.post("/v1/admin/tour-guides")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .build()
        );
    }

    private GatewayFilterChain trackingChain(AtomicBoolean chainCalled) {
        return exchange -> {
            chainCalled.set(true);
            return Mono.empty();
        };
    }
}
