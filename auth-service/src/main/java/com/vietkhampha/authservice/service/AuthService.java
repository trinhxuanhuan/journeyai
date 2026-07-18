package com.vietkhampha.authservice.service;

import com.vietkhampha.authservice.dto.AuthTokenResponse;
import com.vietkhampha.authservice.dto.LoginRequest;
import com.vietkhampha.authservice.dto.RegisterRequest;
import com.vietkhampha.authservice.dto.RegisterResponse;
import com.vietkhampha.authservice.dto.VerifyOtpRequest;
import com.vietkhampha.authservice.entity.OtpVerification;
import com.vietkhampha.authservice.entity.RefreshToken;
import com.vietkhampha.authservice.entity.User;
import com.vietkhampha.authservice.exception.BusinessException;
import com.vietkhampha.authservice.exception.ErrorCode;
import com.vietkhampha.authservice.repository.OtpVerificationRepository;
import com.vietkhampha.authservice.repository.RefreshTokenRepository;
import com.vietkhampha.authservice.repository.UserRepository;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.NoSuchElementException;

@Service
public class AuthService {

    private static final int OTP_LENGTH = 6;
    private static final long OTP_TTL_MINUTES = 5;
    private static final int MAX_OTP_ATTEMPTS = 5;
    private static final long REFRESH_TOKEN_TTL_DAYS = 30;

    // UC-A02 nhánh 3: sai mật khẩu quá 5 lần liên tiếp -> khóa tạm 15 phút.
    // Dùng Redis (không phải cột DB) vì đây là trạng thái tạm thời, tự hết hạn —
    // tách biệt với users.status=LOCKED (dành riêng cho Admin khóa, UC-H02).
    private static final int MAX_LOGIN_ATTEMPTS = 5;
    private static final Duration LOGIN_LOCK_DURATION = Duration.ofMinutes(15);

    private final UserRepository userRepository;
    private final OtpVerificationRepository otpVerificationRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final JwtService jwtService;
    private final StringRedisTemplate redisTemplate;
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthService(
            UserRepository userRepository,
            OtpVerificationRepository otpVerificationRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordEncoder passwordEncoder,
            EmailService emailService,
            JwtService jwtService,
            StringRedisTemplate redisTemplate
    ) {
        this.userRepository = userRepository;
        this.otpVerificationRepository = otpVerificationRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
        this.jwtService = jwtService;
        this.redisTemplate = redisTemplate;
    }

    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }

        String hashedPassword = passwordEncoder.encode(request.getPassword());
        User user = new User(request.getEmail(), hashedPassword, request.getFullName());
        User savedUser = userRepository.save(user);

        Instant otpExpiresAt = createAndSendOtp(savedUser);

        return new RegisterResponse(savedUser.getId(), savedUser.getStatus().name(), otpExpiresAt);
    }

    @Transactional
    public AuthTokenResponse verifyOtp(VerifyOtpRequest request) {
        OtpVerification otp = otpVerificationRepository
                .findFirstByUserIdOrderByCreatedAtDesc(request.getUserId())
                .orElseThrow(() -> new BusinessException(ErrorCode.OTP_INVALID));

        if (otp.getAttemptCount() >= MAX_OTP_ATTEMPTS) {
            throw new BusinessException(ErrorCode.OTP_ATTEMPTS_EXCEEDED);
        }

        if (otp.isExpired()) {
            throw new BusinessException(ErrorCode.OTP_EXPIRED);
        }

        if (otp.isUsed()) {
            throw new BusinessException(ErrorCode.OTP_INVALID);
        }

        boolean matches = passwordEncoder.matches(request.getOtpCode(), otp.getOtpCodeHash());
        if (!matches) {
            otp.incrementAttempt();
            otpVerificationRepository.save(otp);
            throw new BusinessException(ErrorCode.OTP_INVALID);
        }

        otp.markAsUsed();
        otpVerificationRepository.save(otp);

        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new NoSuchElementException("User khong ton tai"));
        user.setStatus(User.Status.ACTIVE);
        userRepository.save(user);

        return issueTokens(user);
    }

    @Transactional
    public AuthTokenResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CREDENTIALS));

        if (isAccountLocked(user.getId())) {
            throw new BusinessException(ErrorCode.ACCOUNT_LOCKED);
        }

        if (user.getStatus() == User.Status.LOCKED) {
            throw new BusinessException(ErrorCode.ACCOUNT_SUSPENDED);
        }

        if (user.getStatus() == User.Status.UNVERIFIED) {
            throw new BusinessException(ErrorCode.ACCOUNT_UNVERIFIED);
        }

        // passwordHash có thể null nếu tài khoản chỉ đăng nhập qua Google (UC-A02
        // trường hợp b, chưa triển khai ở task này) — không thể xác thực bằng
        // mật khẩu trong trường hợp đó.
        boolean matches = user.getPasswordHash() != null
                && passwordEncoder.matches(request.getPassword(), user.getPasswordHash());

        if (!matches) {
            registerFailedAttempt(user.getId());
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }

        clearFailedAttempts(user.getId());
        return issueTokens(user);
    }

    private boolean isAccountLocked(java.util.UUID userId) {
        return redisTemplate.hasKey(loginLockKey(userId));
    }

    private void registerFailedAttempt(java.util.UUID userId) {
        String key = loginAttemptKey(userId);
        Long attempts = redisTemplate.opsForValue().increment(key);
        if (attempts != null && attempts == 1) {
            // Chỉ set TTL ở lần sai đầu tiên — đếm "5 lần liên tiếp trong 1 cửa sổ 15 phút"
            redisTemplate.expire(key, LOGIN_LOCK_DURATION);
        }
        if (attempts != null && attempts >= MAX_LOGIN_ATTEMPTS) {
            redisTemplate.opsForValue().set(loginLockKey(userId), "1", LOGIN_LOCK_DURATION);
            redisTemplate.delete(key);
        }
    }

    private void clearFailedAttempts(java.util.UUID userId) {
        redisTemplate.delete(loginAttemptKey(userId));
        redisTemplate.delete(loginLockKey(userId));
    }

    private String loginAttemptKey(java.util.UUID userId) {
        return "login:fail:" + userId;
    }

    private String loginLockKey(java.util.UUID userId) {
        return "login:lock:" + userId;
    }

    private AuthTokenResponse issueTokens(User user) {
        String accessToken = jwtService.generateAccessToken(user.getId(), user.getRole().name());
        String refreshTokenValue = jwtService.generateRefreshTokenValue();

        RefreshToken refreshToken = new RefreshToken(
                user.getId(),
                passwordEncoder.encode(refreshTokenValue),
                Instant.now().plus(REFRESH_TOKEN_TTL_DAYS, ChronoUnit.DAYS)
        );
        refreshTokenRepository.save(refreshToken);

        return new AuthTokenResponse(accessToken, refreshTokenValue, jwtService.getAccessTokenTtlSeconds());
    }

    Instant createAndSendOtp(User user) {
        String otpCode = generateOtpCode();
        String otpCodeHash = passwordEncoder.encode(otpCode);
        Instant expiresAt = Instant.now().plus(OTP_TTL_MINUTES, ChronoUnit.MINUTES);

        OtpVerification otp = new OtpVerification(user.getId(), otpCodeHash, expiresAt);
        otpVerificationRepository.save(otp);

        emailService.sendOtpEmail(user.getEmail(), otpCode);

        return expiresAt;
    }

    private String generateOtpCode() {
        int code = secureRandom.nextInt(1_000_000);
        return String.format("%0" + OTP_LENGTH + "d", code);
    }
}