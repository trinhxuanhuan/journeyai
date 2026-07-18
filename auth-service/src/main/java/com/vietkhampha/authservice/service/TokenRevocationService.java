package com.vietkhampha.authservice.service;

import com.vietkhampha.authservice.repository.RefreshTokenRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
@Service
public class TokenRevocationService {

    private final RefreshTokenRepository refreshTokenRepository;

    public TokenRevocationService(RefreshTokenRepository refreshTokenRepository) {
        this.refreshTokenRepository = refreshTokenRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void revokeAllTokensForUser(UUID userId) {
        refreshTokenRepository.revokeAllByUserId(userId);
    }
}