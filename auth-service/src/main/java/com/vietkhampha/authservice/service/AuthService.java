package com.vietkhampha.authservice.service;

import com.vietkhampha.authservice.dto.AuthTokenResponse;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.NoSuchElementException;

@Service
public class AuthService {

    private static final int OTP_LENGTH = 6;
    private static final long OTP_TTL_MINUTES = 5;
    private static final int MAX_OTP_ATTEMPTS = 5; // UC-A01 nhánh 3
    private static final long REFRESH_TOKEN_TTL_DAYS = 30;

    private final UserRepository userRepository;
    private final OtpVerificationRepository otpVerificationRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final JwtService jwtService;
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthService(
            UserRepository userRepository,
            OtpVerificationRepository otpVerificationRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordEncoder passwordEncoder,
            EmailService emailService,
            JwtService jwtService
    ) {
        this.userRepository = userRepository;
        this.otpVerificationRepository = otpVerificationRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
        this.jwtService = jwtService;
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

        // UC-A01 nhánh 3: sai quá 5 lần -> khóa 15 phút.
        // Đơn giản hóa hợp lý cho Sprint 1: coi "khóa 15 phút" = "hết hạn OTP hiện
        // tại", buộc phải resend-otp để lấy mã mới -> tự nhiên tạo độ trễ, không cần
        // thêm cột riêng lưu "lockedUntil" (đúng tinh thần schema đã chốt ở ERD.md).
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

    // Dùng chung cho verify-otp (bây giờ) và login (T-A01-2, sau này) —
    // tránh viết lại logic sinh + lưu token ở 2 nơi.
    private AuthTokenResponse issueTokens(User user) {
        String accessToken = jwtService.generateAccessToken(user.getId(), user.getRole().name());
        String refreshTokenValue = jwtService.generateRefreshTokenValue();

        RefreshToken refreshToken = new RefreshToken(
                user.getId(),
                passwordEncoder.encode(refreshTokenValue), // hash trước khi lưu — ERD.md §2
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