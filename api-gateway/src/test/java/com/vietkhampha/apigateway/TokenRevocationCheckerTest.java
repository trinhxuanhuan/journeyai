package com.vietkhampha.apigateway;

import com.vietkhampha.apigateway.security.TokenRevocationChecker;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TokenRevocationCheckerTest {

    @Test
    void usesTheSameSha256Base64BlacklistKeyAsAuthService() {
        ReactiveStringRedisTemplate redisTemplate = mock(ReactiveStringRedisTemplate.class);
        String expectedKey = "token:blacklist:Pxa+1wifRlPl7yG/0oJNfzqq7MelmOfonFgOFgapzFI=";
        when(redisTemplate.hasKey(expectedKey)).thenReturn(Mono.just(true));
        TokenRevocationChecker checker = new TokenRevocationChecker(redisTemplate);

        assertTrue(checker.isRevoked("access-token").block());

        verify(redisTemplate).hasKey(expectedKey);
    }
}
