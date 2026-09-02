package com.vietkhampha.apigateway.security;

import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

@Component
public class TokenRevocationChecker {

    private static final String BLACKLIST_KEY_PREFIX = "token:blacklist:";

    private final ReactiveStringRedisTemplate redisTemplate;

    public TokenRevocationChecker(ReactiveStringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public Mono<Boolean> isRevoked(String accessToken) {
        return redisTemplate.hasKey(BLACKLIST_KEY_PREFIX + sha256Base64(accessToken));
    }

    private String sha256Base64(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 không khả dụng", exception);
        }
    }
}
