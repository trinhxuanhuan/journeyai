package com.vietkhampha.apigateway;

import com.vietkhampha.apigateway.security.JwtAuthenticationFilter;
import com.vietkhampha.apigateway.security.JwtValidator;
import com.vietkhampha.apigateway.security.TokenRevocationChecker;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

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

class AccountGatewaySecurityTest {

    @Test
    void authPingIsPublicForReadinessChecks() {
        JwtValidator jwtValidator = mock(JwtValidator.class);
        TokenRevocationChecker tokenRevocationChecker = mock(TokenRevocationChecker.class);
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtValidator, tokenRevocationChecker);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/v1/auth/ping").build()
        );
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.filter(exchange, received -> {
            chainCalled.set(true);
            return Mono.empty();
        }).block(Duration.ofSeconds(1));

        assertEquals(null, exchange.getResponse().getStatusCode());
        assertEquals(true, chainCalled.get());
        verifyNoInteractions(jwtValidator, tokenRevocationChecker);
    }

    @Test
    void authAndProfileMeEndpointsRequireJwt() {
        assertRequiresJwt("/v1/auth/me");
        assertRequiresJwt("/v1/users/me");
    }

    @Test
    void authenticatedAccountRequestReceivesTrustedIdentityInsteadOfSpoofedHeaders() {
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
                MockServerHttpRequest.get("/v1/auth/me")
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

        assertEquals(List.of(customerId), forwarded.get().getRequest().getHeaders().get("X-User-Id"));
        assertEquals(List.of("CUSTOMER"), forwarded.get().getRequest().getHeaders().get("X-User-Role"));
    }

    @Test
    void revokedAccessTokenIsRejectedBeforeRouting() {
        JwtValidator jwtValidator = mock(JwtValidator.class);
        TokenRevocationChecker tokenRevocationChecker = mock(TokenRevocationChecker.class);
        Claims claims = mock(Claims.class);
        when(jwtValidator.validateAndParse("revoked-token")).thenReturn(claims);
        when(tokenRevocationChecker.isRevoked("revoked-token")).thenReturn(Mono.just(true));
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtValidator, tokenRevocationChecker);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/v1/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer revoked-token")
                        .build()
        );
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.filter(exchange, received -> {
            chainCalled.set(true);
            return Mono.empty();
        }).block(Duration.ofSeconds(1));

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        assertFalse(chainCalled.get());
    }

    private void assertRequiresJwt(String path) {
        JwtValidator jwtValidator = mock(JwtValidator.class);
        TokenRevocationChecker tokenRevocationChecker = mock(TokenRevocationChecker.class);
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtValidator, tokenRevocationChecker);
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get(path).build());
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.filter(exchange, received -> {
            chainCalled.set(true);
            return Mono.empty();
        }).block(Duration.ofSeconds(1));

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        assertFalse(chainCalled.get());
        verifyNoInteractions(jwtValidator, tokenRevocationChecker);
    }
}
