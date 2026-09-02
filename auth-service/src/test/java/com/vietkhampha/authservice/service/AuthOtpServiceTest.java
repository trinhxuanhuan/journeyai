package com.vietkhampha.authservice.service;

import com.vietkhampha.authservice.dto.LoginRequest;
import com.vietkhampha.authservice.dto.ResendOtpRequest;
import com.vietkhampha.authservice.dto.ResendOtpResponse;
import com.vietkhampha.authservice.entity.OtpVerification;
import com.vietkhampha.authservice.entity.User;
import com.vietkhampha.authservice.event.AuthEventPublisher;
import com.vietkhampha.authservice.exception.AccountVerificationRequiredException;
import com.vietkhampha.authservice.exception.BusinessException;
import com.vietkhampha.authservice.exception.ErrorCode;
import com.vietkhampha.authservice.repository.OtpVerificationRepository;
import com.vietkhampha.authservice.repository.RefreshTokenRepository;
import com.vietkhampha.authservice.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthOtpServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private OtpVerificationRepository otpVerificationRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private EmailService emailService;
    @Mock private JwtService jwtService;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;
    @Mock private TokenRevocationService tokenRevocationService;
    @Mock private AuthEventPublisher authEventPublisher;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                userRepository,
                otpVerificationRepository,
                refreshTokenRepository,
                passwordEncoder,
                emailService,
                jwtService,
                redisTemplate,
                tokenRevocationService,
                authEventPublisher
        );
    }

    @Test
    void resendOtpInvalidatesPreviousCodeAndReturnsServerTiming() {
        UUID userId = UUID.randomUUID();
        User user = unverifiedUser(userId, "khach@example.com");
        OtpVerification previous = new OtpVerification(
                userId,
                "old-hash",
                Instant.now().plusSeconds(30)
        );
        ResendOtpRequest request = resendRequest(userId);

        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(user));
        allowOtpDelivery(2L);
        when(otpVerificationRepository.findAllByUserIdAndUsedFalse(userId))
                .thenReturn(List.of(previous));
        when(passwordEncoder.encode(anyString())).thenReturn("new-hash");

        Instant before = Instant.now();
        ResendOtpResponse response = authService.resendOtp(request);

        assertThat(previous.isUsed()).isTrue();
        assertThat(response.getOtpExpiresAt()).isAfter(before.plusSeconds(4 * 60));
        assertThat(response.getOtpResendAvailableAt()).isAfter(before.plusSeconds(50));
        verify(otpVerificationRepository).saveAll(List.of(previous));
        verify(otpVerificationRepository).save(any(OtpVerification.class));
        verify(emailService).sendOtpEmail(eq("khach@example.com"), anyString());
    }

    @Test
    void resendOtpRejectsRequestsInsideCooldownWithoutCreatingAnotherCode() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findByIdForUpdate(userId))
                .thenReturn(Optional.of(unverifiedUser(userId, "khach@example.com")));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), eq("1"), eq(Duration.ofSeconds(60))))
                .thenReturn(false);

        assertThatThrownBy(() -> authService.resendOtp(resendRequest(userId)))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.OTP_RESEND_COOLDOWN)
                );

        verify(otpVerificationRepository, never()).save(any());
        verify(emailService, never()).sendOtpEmail(anyString(), anyString());
    }

    @Test
    void resendOtpRejectsSixthDeliveryInsideHourlyWindow() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findByIdForUpdate(userId))
                .thenReturn(Optional.of(unverifiedUser(userId, "khach@example.com")));
        allowOtpDelivery(6L);

        assertThatThrownBy(() -> authService.resendOtp(resendRequest(userId)))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.OTP_SEND_LIMIT_EXCEEDED)
                );

        verify(otpVerificationRepository, never()).save(any());
        verify(emailService, never()).sendOtpEmail(anyString(), anyString());
    }

    @Test
    void resendOtpDoesNotSendForActiveOrUnknownAccount() {
        UUID userId = UUID.randomUUID();
        User active = unverifiedUser(userId, "khach@example.com");
        active.setStatus(User.Status.ACTIVE);
        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(active));

        assertThatThrownBy(() -> authService.resendOtp(resendRequest(userId)))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.OTP_RESEND_NOT_ALLOWED)
                );

        verify(redisTemplate, never()).opsForValue();
        verify(emailService, never()).sendOtpEmail(anyString(), anyString());
    }

    @Test
    void validLoginForUnverifiedAccountReturnsSafeResumeContext() {
        UUID userId = UUID.randomUUID();
        User user = unverifiedUser(userId, "khach@example.com");
        OtpVerification otp = new OtpVerification(
                userId,
                "hash",
                Instant.now().plusSeconds(120)
        );
        LoginRequest request = loginRequest("khach@example.com", "correct-password");

        when(userRepository.findByEmail("khach@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("correct-password", "password-hash")).thenReturn(true);
        when(otpVerificationRepository.findFirstByUserIdOrderByCreatedAtDesc(userId))
                .thenReturn(Optional.of(otp));

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOfSatisfying(AccountVerificationRequiredException.class, exception -> {
                    assertThat(exception.getUserId()).isEqualTo(userId);
                    assertThat(exception.getEmail()).isEqualTo("khach@example.com");
                    assertThat(exception.getOtpExpiresAt()).isEqualTo(otp.getExpiresAt());
                });
    }

    @Test
    void wrongPasswordNeverRevealsThatAccountIsUnverified() {
        UUID userId = UUID.randomUUID();
        User user = unverifiedUser(userId, "khach@example.com");
        LoginRequest request = loginRequest("khach@example.com", "wrong-password");

        when(userRepository.findByEmail("khach@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong-password", "password-hash")).thenReturn(false);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(anyString())).thenReturn(1L);

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_CREDENTIALS)
                );

        verify(otpVerificationRepository, never())
                .findFirstByUserIdOrderByCreatedAtDesc(userId);
    }

    private void allowOtpDelivery(long sendCount) {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), eq("1"), eq(Duration.ofSeconds(60))))
                .thenReturn(true);
        when(valueOperations.increment(anyString())).thenReturn(sendCount);
    }

    private User unverifiedUser(UUID userId, String email) {
        User user = new User(email, "password-hash", "Nguyễn An");
        ReflectionTestUtils.setField(user, "id", userId);
        return user;
    }

    private ResendOtpRequest resendRequest(UUID userId) {
        ResendOtpRequest request = new ResendOtpRequest();
        request.setUserId(userId);
        return request;
    }

    private LoginRequest loginRequest(String email, String password) {
        LoginRequest request = new LoginRequest();
        request.setEmail(email);
        request.setPassword(password);
        return request;
    }
}
