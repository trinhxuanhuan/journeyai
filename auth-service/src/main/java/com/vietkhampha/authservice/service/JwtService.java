package com.vietkhampha.authservice.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {

    private final SecretKey signingKey;
    private final long accessTokenTtlSeconds;

    public JwtService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.access-token-ttl-seconds}") long accessTokenTtlSeconds
    ) {
        // Secret cần đủ dài (≥32 ký tự) cho thuật toán HS256 — nhắc ở .env.example
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenTtlSeconds = accessTokenTtlSeconds;
    }

    public String generateAccessToken(UUID userId, String role) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(userId.toString())          // claim "sub" — AI Service đọc claim này (AI_PIPELINE.md §2.3.1)
                .claim("role", role)                   // claim "role" — Gateway dùng để phân quyền (ARCHITECTURE.md §6.1)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(accessTokenTtlSeconds, ChronoUnit.SECONDS)))
                .signWith(signingKey)
                .compact();
    }

    // Refresh token: chuỗi ngẫu nhiên độc lập, KHÔNG phải JWT — vì refresh token
    // chỉ cần tra cứu trong bảng refresh_tokens (ERD.md §2), không cần tự chứa claim.
    public String generateRefreshTokenValue() {
        return UUID.randomUUID().toString() + "-" + UUID.randomUUID();
    }

    public long getAccessTokenTtlSeconds() {
        return accessTokenTtlSeconds;
    }

    public Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}