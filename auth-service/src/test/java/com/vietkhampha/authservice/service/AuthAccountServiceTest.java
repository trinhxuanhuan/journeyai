package com.vietkhampha.authservice.service;

import com.vietkhampha.authservice.dto.CurrentUserResponse;
import com.vietkhampha.authservice.dto.UpdateCurrentUserRequest;
import com.vietkhampha.authservice.entity.User;
import com.vietkhampha.authservice.event.AuthEventPublisher;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthAccountServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private OtpVerificationRepository otpVerificationRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private EmailService emailService;
    @Mock private JwtService jwtService;
    @Mock private StringRedisTemplate redisTemplate;
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
    void getCurrentUserReturnsIdentityOwnedByAuthService() {
        UUID userId = UUID.randomUUID();
        User user = activeUser(userId, "khach@example.com", "Nguyễn An");
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        CurrentUserResponse response = authService.getCurrentUser(userId);

        assertThat(response.getUserId()).isEqualTo(userId);
        assertThat(response.getEmail()).isEqualTo("khach@example.com");
        assertThat(response.getFullName()).isEqualTo("Nguyễn An");
        assertThat(response.getRole()).isEqualTo("CUSTOMER");
        assertThat(response.getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    void updateCurrentUserNormalizesWhitespaceAndKeepsEmailReadOnly() {
        UUID userId = UUID.randomUUID();
        User user = activeUser(userId, "khach@example.com", "Tên cũ");
        UpdateCurrentUserRequest request = new UpdateCurrentUserRequest();
        request.setFullName("  Trịnh   Xuân Huấn  ");
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CurrentUserResponse response = authService.updateCurrentUser(userId, request);

        assertThat(response.getFullName()).isEqualTo("Trịnh Xuân Huấn");
        assertThat(response.getEmail()).isEqualTo("khach@example.com");
        verify(userRepository).save(user);
    }

    @Test
    void getCurrentUserReturnsDomainNotFoundInsteadOfGenericFailure() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.getCurrentUser(userId))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.USER_NOT_FOUND)
                );
    }

    private User activeUser(UUID userId, String email, String fullName) {
        User user = new User(email, "hash", fullName);
        ReflectionTestUtils.setField(user, "id", userId);
        user.setStatus(User.Status.ACTIVE);
        return user;
    }
}
