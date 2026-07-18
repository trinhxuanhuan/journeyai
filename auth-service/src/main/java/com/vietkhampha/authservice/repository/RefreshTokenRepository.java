package com.vietkhampha.authservice.repository;

import com.vietkhampha.authservice.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {
}