package com.vietkhampha.authservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Cấu hình bảo mật tạm thời cho Sprint 0.
 *
 * Lưu ý quan trọng (ARCHITECTURE.md §6.1): JWT validation tập trung ở
 * API Gateway — auth-service (và các service Java khác) TIN TƯỞNG JWT đã
 * được Gateway xác thực, không tự validate lại. Cấu hình đầy đủ (JWT filter
 * đọc claim role, áp dụng cho các route /admin/**...) sẽ được thêm ở
 * Sprint 1 cùng US-A01/A07. Ở Sprint 0 chỉ mở endpoint public để test hạ
 * tầng, chưa có logic phân quyền thật.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/v1/auth/ping", "/actuator/health", "/actuator/info").permitAll()
                        .anyRequest().permitAll() // TODO Sprint 1: siết lại theo JWT claim role (US-A07)
                );
        return http.build();
    }

}
