package com.vietkhampha.apigateway;

import com.vietkhampha.apigateway.security.JwtValidator;
import com.vietkhampha.apigateway.security.TokenRevocationChecker;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayCorsIntegrationTest {

    private static final String ALLOWED_ORIGIN = "http://localhost:3000";
    private static final String ALTERNATE_LOCAL_ORIGIN = "http://127.0.0.1:3000";
    private static final String DISALLOWED_ORIGIN = "https://untrusted.example";

    @LocalServerPort
    private int port;

    private WebTestClient webTestClient;

    @MockBean
    private JwtValidator jwtValidator;

    @MockBean
    private TokenRevocationChecker tokenRevocationChecker;

    @BeforeEach
    void setUpWebTestClient() {
        when(tokenRevocationChecker.isRevoked(anyString())).thenReturn(Mono.just(false));
        webTestClient = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .build();
    }

    @ParameterizedTest(name = "preflight {0} {1}")
    @MethodSource("allowedPreflightRequests")
    void allowedFrontendOrigin_preflightSucceedsWithoutJwt(
            String path,
            HttpMethod requestedMethod,
            List<String> requestedHeaders
    ) {
        webTestClient.options()
                .uri(path)
                .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, requestedMethod.name())
                .header(
                        HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS,
                        String.join(",", requestedHeaders)
                )
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ALLOWED_ORIGIN)
                .expectHeader().value(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS,
                        value -> assertContainsIgnoreCase(value, requestedMethod.name())
                )
                .expectHeader().value(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS,
                        value -> requestedHeaders.forEach(header -> assertContainsIgnoreCase(value, header))
                )
                .expectHeader().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS);
    }

    @Test
    void disallowedOrigin_preflightIsRejectedWithoutCorsAccess() {
        webTestClient.options()
                .uri("/v1/bookings")
                .header(HttpHeaders.ORIGIN, DISALLOWED_ORIGIN)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.POST.name())
                .header(
                        HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS,
                        "Authorization,Content-Type,Idempotency-Key"
                )
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.FORBIDDEN)
                .expectHeader().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN);
    }

    @Test
    void alternateLocalOrigin_preflightIsAllowed() {
        webTestClient.options()
                .uri("/v1/tours")
                .header(HttpHeaders.ORIGIN, ALTERNATE_LOCAL_ORIGIN)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.GET.name())
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN,
                        ALTERNATE_LOCAL_ORIGIN
                );
    }

    @Test
    void trustedIdentityHeader_cannotBeRequestedByBrowser() {
        webTestClient.options()
                .uri("/v1/bookings")
                .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.POST.name())
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "X-User-Id")
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.FORBIDDEN)
                .expectHeader().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS);
    }

    @Test
    void protectedActualRequestWithoutJwt_remainsUnauthorizedAndGetsCorsHeader() {
        webTestClient.get()
                .uri("/v1/bookings/{id}", UUID.randomUUID())
                .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ALLOWED_ORIGIN);
    }

    @Test
    void adminActualRequestWithCustomerJwt_remainsForbiddenAndGetsCorsHeader() {
        Claims claims = mock(Claims.class);
        when(claims.getSubject()).thenReturn(UUID.randomUUID().toString());
        when(claims.get("role", String.class)).thenReturn("CUSTOMER");
        when(jwtValidator.validateAndParse("customer-token")).thenReturn(claims);

        webTestClient.post()
                .uri("/v1/admin/tours")
                .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                .header(HttpHeaders.AUTHORIZATION, "Bearer customer-token")
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .bodyValue("{}")
                .exchange()
                .expectStatus().isForbidden()
                .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ALLOWED_ORIGIN);
    }

    private static Stream<Arguments> allowedPreflightRequests() {
        return Stream.of(
                Arguments.of(
                        "/v1/auth/login",
                        HttpMethod.POST,
                        List.of("Content-Type")
                ),
                Arguments.of(
                        "/v1/bookings",
                        HttpMethod.POST,
                        List.of("Authorization", "Content-Type", "Idempotency-Key")
                ),
                Arguments.of(
                        "/v1/admin/tours",
                        HttpMethod.POST,
                        List.of("Authorization", "Content-Type")
                )
        );
    }

    private void assertContainsIgnoreCase(String actual, String expected) {
        boolean present = Stream.of(actual.split(","))
                .map(String::trim)
                .anyMatch(value -> value.equalsIgnoreCase(expected));
        if (!present) {
            throw new AssertionError("Expected header value '" + actual + "' to contain '" + expected + "'");
        }
    }
}
