package com.vietkhampha.authservice.controller;

import com.vietkhampha.authservice.dto.AuthTokenResponse;
import com.vietkhampha.authservice.dto.CurrentUserResponse;
import com.vietkhampha.authservice.dto.LoginRequest;
import com.vietkhampha.authservice.dto.RefreshTokenRequest;
import com.vietkhampha.authservice.dto.RegisterRequest;
import com.vietkhampha.authservice.dto.RegisterResponse;
import com.vietkhampha.authservice.dto.ResendOtpRequest;
import com.vietkhampha.authservice.dto.ResendOtpResponse;
import com.vietkhampha.authservice.dto.UpdateCurrentUserRequest;
import com.vietkhampha.authservice.dto.VerifyOtpRequest;
import com.vietkhampha.authservice.exception.BusinessException;
import com.vietkhampha.authservice.exception.ErrorCode;
import com.vietkhampha.authservice.service.AuthService;
import com.vietkhampha.authservice.service.JwtService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final JwtService jwtService;

    public AuthController(AuthService authService, JwtService jwtService) {
        this.authService = authService;
        this.jwtService = jwtService;
    }

    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @PostMapping("/verify-otp")
    public ResponseEntity<AuthTokenResponse> verifyOtp(@Valid @RequestBody VerifyOtpRequest request) {
        return ResponseEntity.ok(authService.verifyOtp(request));
    }

    @PostMapping("/resend-otp")
    public ResponseEntity<ResendOtpResponse> resendOtp(@Valid @RequestBody ResendOtpRequest request) {
        return ResponseEntity.ok(authService.resendOtp(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthTokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthTokenResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(authService.refresh(request));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @RequestHeader("Authorization") String authorizationHeader,
            @Valid @RequestBody RefreshTokenRequest request
    ) {
        String accessToken = extractBearerToken(authorizationHeader);
        authService.logout(request.getRefreshToken(), accessToken);
        return ResponseEntity.noContent().build(); // 204 — không cần trả nội dung
    }

    @PostMapping("/logout-all")
    public ResponseEntity<Void> logoutAll(@RequestHeader("Authorization") String authorizationHeader) {
        String accessToken = extractBearerToken(authorizationHeader);
        UUID userId = UUID.fromString(jwtService.parseClaims(accessToken).getSubject());
        authService.logoutAll(userId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public ResponseEntity<CurrentUserResponse> getCurrentUser(
            @RequestHeader("X-User-Id") String userIdHeader
    ) {
        return ResponseEntity.ok(authService.getCurrentUser(UUID.fromString(userIdHeader)));
    }

    @PatchMapping("/me")
    public ResponseEntity<CurrentUserResponse> updateCurrentUser(
            @RequestHeader("X-User-Id") String userIdHeader,
            @Valid @RequestBody UpdateCurrentUserRequest request
    ) {
        return ResponseEntity.ok(authService.updateCurrentUser(UUID.fromString(userIdHeader), request));
    }

    // Dùng chung cho logout/logout-all — tránh lặp logic tách "Bearer " ở 2 nơi.
    private String extractBearerToken(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID, "Thiếu hoặc sai định dạng Authorization header");
        }
        return authorizationHeader.substring(7);
    }

}
