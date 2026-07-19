package com.vietkhampha.apigateway.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

@Component
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    private static final List<String> PUBLIC_PATHS = List.of(
            "/v1/auth/register",
            "/v1/auth/verify-otp",
            "/v1/auth/resend-otp",
            "/v1/auth/login",
            "/v1/auth/refresh",
            "/v1/auth/google/**",
            "/v1/tours",
            "/v1/tours/**",
            "/actuator/**"
    );

    private final JwtValidator jwtValidator;

    public JwtAuthenticationFilter(JwtValidator jwtValidator) {
        this.jwtValidator = jwtValidator;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();

        if (isPublicPath(path)) {
            return chain.filter(exchange);
        }

        String token = extractBearerToken(exchange.getRequest());
        if (token == null) {
            return reject(exchange, HttpStatus.UNAUTHORIZED);
        }

        Claims claims;
        try {
            claims = jwtValidator.validateAndParse(token);
        } catch (JwtException e) {
            return reject(exchange, HttpStatus.UNAUTHORIZED);
        }

        if (path.startsWith("/v1/admin/") && !"ADMIN".equals(claims.get("role", String.class))) {
            return reject(exchange, HttpStatus.FORBIDDEN);
        }

        // Chuyển tiếp thông tin user đã xác thực sang service phía sau qua header —
        // các service Java (đang permitAll()) có thể đọc header này sau này khi
        // cần biết "ai đang gọi" mà không phải tự parse JWT lại lần nữa.
        ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                .header("X-User-Id", claims.getSubject())
                .header("X-User-Role", claims.get("role", String.class))
                .build();

        return chain.filter(exchange.mutate().request(mutatedRequest).build());
    }

    private boolean isPublicPath(String path) {
        return PUBLIC_PATHS.stream().anyMatch(pattern -> PATH_MATCHER.match(pattern, path));
    }

    private String extractBearerToken(ServerHttpRequest request) {
        String header = request.getHeaders().getFirst("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }

    private Mono<Void> reject(ServerWebExchange exchange, HttpStatus status) {
        exchange.getResponse().setStatusCode(status);
        return exchange.getResponse().setComplete();
    }

    @Override
    public int getOrder() {
        return -1; // Chạy sớm nhất trong chuỗi filter — chặn trước khi routing thật sự xảy ra
    }
}