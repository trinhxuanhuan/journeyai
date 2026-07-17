package com.vietkhampha.authservice.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

/**
 * Sprint 0 (T-000-1): endpoint xác nhận service đã build/chạy/kết nối
 * PostgreSQL thành công qua Docker Compose. Sẽ được thay bằng
 * AuthController thật (UC-A01/A02/A03) ở Sprint 1.
 *
 * Base path "/v1/auth" khớp đúng API_CONTRACT.md §2.
 */
@RestController
@RequestMapping("/v1/auth")
public class PingController {

    @GetMapping("/ping")
    public Map<String, Object> ping() {
        return Map.of(
                "service", "auth-service",
                "status", "UP",
                "timestamp", Instant.now().toString()
        );
    }

}
