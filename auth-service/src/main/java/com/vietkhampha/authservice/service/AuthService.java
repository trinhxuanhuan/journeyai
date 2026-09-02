package com.vietkhampha.authservice.service;

import com.vietkhampha.authservice.dto.*;
import com.vietkhampha.authservice.entity.OtpVerification;
import com.vietkhampha.authservice.entity.RefreshToken;
import com.vietkhampha.authservice.entity.User;
import com.vietkhampha.authservice.exception.AccountVerificationRequiredException;
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
    private static final Duration OTP_RESEND_COOLDOWN = Duration.ofSeconds(60);
    private static final Duration OTP_SEND_WINDOW = Duration.ofHours(1);
    private static final int MAX_OTP_SENDS_PER_WINDOW = 5;
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
    private final TokenRevocationService tokenRevocationService;
    private final com.vietkhampha.authservice.event.AuthEventPublisher authEventPublisher;

    public AuthService(
            UserRepository userRepository,
            OtpVerificationRepository otpVerificationRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordEncoder passwordEncoder,
            EmailService emailService,
            JwtService jwtService,
            StringRedisTemplate redisTemplate,
            TokenRevocationService tokenRevocationService,
            com.vietkhampha.authservice.event.AuthEventPublisher authEventPublisher
    ) {
        this.userRepository = userRepository;
        this.otpVerificationRepository = otpVerificationRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
        this.jwtService = jwtService;
        this.redisTemplate = redisTemplate;
        this.tokenRevocationService = tokenRevocationService;
        this.authEventPublisher = authEventPublisher;
    }

    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }

        String hashedPassword = passwordEncoder.encode(request.getPassword());
        User user = new User(request.getEmail(), hashedPassword, request.getFullName());
        User savedUser = userRepository.save(user);

        OtpDeliveryWindow otpWindow = createAndSendOtp(savedUser);

        return new RegisterResponse(
                savedUser.getId(),
                savedUser.getStatus().name(),
                otpWindow.expiresAt(),
                otpWindow.resendAvailableAt()
        );
    }

    @Transactional
    public AuthTokenResponse verifyOtp(VerifyOtpRequest request) {
        User user = userRepository.findByIdForUpdate(request.getUserId())
                .filter(candidate -> candidate.getStatus() == User.Status.UNVERIFIED)
                .orElseThrow(() -> new BusinessException(ErrorCode.OTP_INVALID));

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

        user.setStatus(User.Status.ACTIVE);
        userRepository.save(user);
        authEventPublisher.publishUserRegistered(user.getId(), user.getEmail(), user.getFullName());

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

        if (user.getStatus() == User.Status.UNVERIFIED) {
            OtpDeliveryWindow otpWindow = currentOtpWindow(user.getId());
            throw new AccountVerificationRequiredException(
                    user.getId(),
                    user.getEmail(),
                    otpWindow.expiresAt(),
                    otpWindow.resendAvailableAt()
            );
        }

        return issueTokens(user);
    }

    @Transactional
    public ResendOtpResponse resendOtp(ResendOtpRequest request) {
        User user = userRepository.findByIdForUpdate(request.getUserId())
                .filter(candidate -> candidate.getStatus() == User.Status.UNVERIFIED)
                .orElseThrow(() -> new BusinessException(ErrorCode.OTP_RESEND_NOT_ALLOWED));

        OtpDeliveryWindow otpWindow = createAndSendOtp(user);
        return new ResendOtpResponse(otpWindow.expiresAt(), otpWindow.resendAvailableAt());
    }

    @Transactional(readOnly = true)
    public CurrentUserResponse getCurrentUser(java.util.UUID userId) {
        return CurrentUserResponse.from(findUserOrThrow(userId));
    }

    @Transactional
    public CurrentUserResponse updateCurrentUser(
            java.util.UUID userId,
            UpdateCurrentUserRequest request
    ) {
        User user = findUserOrThrow(userId);
        user.updateFullName(request.getFullName().trim().replaceAll("\\s+", " "));
        return CurrentUserResponse.from(userRepository.save(user));
    }

    private User findUserOrThrow(java.util.UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
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
                jwtService.hashRefreshToken(refreshTokenValue),
                Instant.now().plus(REFRESH_TOKEN_TTL_DAYS, ChronoUnit.DAYS)
        );
        refreshTokenRepository.save(refreshToken);

        return new AuthTokenResponse(accessToken, refreshTokenValue, jwtService.getAccessTokenTtlSeconds());
    }

    OtpDeliveryWindow createAndSendOtp(User user) {
        reserveOtpDelivery(user.getId());

        var unusedOtps = otpVerificationRepository.findAllByUserIdAndUsedFalse(user.getId());
        unusedOtps.forEach(OtpVerification::markAsUsed);
        if (!unusedOtps.isEmpty()) {
            otpVerificationRepository.saveAll(unusedOtps);
        }

        String otpCode = generateOtpCode();
        String otpCodeHash = passwordEncoder.encode(otpCode);
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plus(OTP_TTL_MINUTES, ChronoUnit.MINUTES);

        OtpVerification otp = new OtpVerification(user.getId(), otpCodeHash, expiresAt);
        otpVerificationRepository.save(otp);

        emailService.sendOtpEmail(user.getEmail(), otpCode);

        return new OtpDeliveryWindow(expiresAt, issuedAt.plus(OTP_RESEND_COOLDOWN));
    }

    private void reserveOtpDelivery(java.util.UUID userId) {
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(
                otpResendCooldownKey(userId),
                "1",
                OTP_RESEND_COOLDOWN
        );
        if (!Boolean.TRUE.equals(acquired)) {
            throw new BusinessException(ErrorCode.OTP_RESEND_COOLDOWN);
        }

        String sendWindowKey = otpSendWindowKey(userId);
        Long sendCount = redisTemplate.opsForValue().increment(sendWindowKey);
        if (sendCount != null && sendCount == 1L) {
            redisTemplate.expire(sendWindowKey, OTP_SEND_WINDOW);
        }
        if (sendCount == null || sendCount > MAX_OTP_SENDS_PER_WINDOW) {
            throw new BusinessException(ErrorCode.OTP_SEND_LIMIT_EXCEEDED);
        }
    }

    private OtpDeliveryWindow currentOtpWindow(java.util.UUID userId) {
        return otpVerificationRepository.findFirstByUserIdOrderByCreatedAtDesc(userId)
                .map(otp -> new OtpDeliveryWindow(
                        otp.getExpiresAt(),
                        otp.getCreatedAt().plus(OTP_RESEND_COOLDOWN)
                ))
                .orElseGet(() -> {
                    Instant now = Instant.now();
                    return new OtpDeliveryWindow(now, now);
                });
    }

    private String otpResendCooldownKey(java.util.UUID userId) {
        return "otp:resend:cooldown:" + userId;
    }

    private String otpSendWindowKey(java.util.UUID userId) {
        return "otp:send:window:" + userId;
    }

    record OtpDeliveryWindow(Instant expiresAt, Instant resendAvailableAt) {
    }

    private String generateOtpCode() {
        int code = secureRandom.nextInt(1_000_000);
        return String.format("%0" + OTP_LENGTH + "d", code);
    }
    @Transactional
    public AuthTokenResponse refresh(RefreshTokenRequest request) {
        String incomingHash = jwtService.hashRefreshToken(request.getRefreshToken());

        RefreshToken storedToken = refreshTokenRepository.findByTokenHash(incomingHash)
                .orElseThrow(() -> new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID));

        // Chuẩn RFC 6819 — Reuse Detection: token đã bị revoke mà vẫn được dùng
        // lại là dấu hiệu bị đánh cắp -> thu hồi TOÀN BỘ token của user, không chỉ
        // từ chối request này. Bảo vệ chủ tài khoản thật kể cả khi không hay biết.
        if (storedToken.isRevoked()) {
            tokenRevocationService.revokeAllTokensForUser(storedToken.getUserId());
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID);
        }

        if (Instant.now().isAfter(storedToken.getExpiresAt())) {
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_EXPIRED);
        }

        User user = userRepository.findById(storedToken.getUserId())
                .orElseThrow(() -> new NoSuchElementException("User khong ton tai"));

        // Rotation: thu hồi token cũ trước khi cấp token mới — không có khoảng hở
        // nào mà cả 2 token cùng hợp lệ.
        storedToken.revoke();
        refreshTokenRepository.save(storedToken);

        return issueTokens(user);
    }

    @Transactional
    public void logout(String refreshTokenValue, String accessTokenValue) {
        String hash = jwtService.hashRefreshToken(refreshTokenValue);
        refreshTokenRepository.findByTokenHash(hash).ifPresent(token -> {
            token.revoke();
            refreshTokenRepository.save(token);
        });

        // Access token còn hiệu lực tối đa 15 phút (accessTokenTtlSeconds) —
        // blacklist chỉ cần tồn tại đúng khoảng thời gian đó, tự hết hạn theo TTL,
        // không cần dọn dẹp thủ công (UC-A03: "đưa access token vào blacklist").
        redisTemplate.opsForValue().set(
                accessTokenBlacklistKey(accessTokenValue),
                "1",
                Duration.ofSeconds(jwtService.getAccessTokenTtlSeconds())
        );
    }

    @Transactional
    public void logoutAll(java.util.UUID userId) {
        refreshTokenRepository.revokeAllByUserId(userId);
        // Không thể blacklist từng access token đang tồn tại ở các thiết bị khác
        // (hệ thống không lưu vết access token đã phát hành) — chấp nhận được vì
        // access token hết hạn tự nhiên sau tối đa 15 phút (ARCHITECTURE.md §6.1).
    }

    private String accessTokenBlacklistKey(String accessToken) {
        return "token:blacklist:" + jwtService.hashRefreshToken(accessToken); // tái dùng SHA-256, tránh lưu token gốc làm key
    }
}
