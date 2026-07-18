package com.vietkhampha.authservice.service;

import com.vietkhampha.authservice.dto.RegisterRequest;
import com.vietkhampha.authservice.dto.RegisterResponse;
import com.vietkhampha.authservice.entity.User;
import com.vietkhampha.authservice.exception.EmailAlreadyExistsException;
import com.vietkhampha.authservice.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new EmailAlreadyExistsException(request.getEmail());
        }

        String hashedPassword = passwordEncoder.encode(request.getPassword());
        User user = new User(request.getEmail(), hashedPassword, request.getFullName());
        User savedUser = userRepository.save(user);

        Instant otpExpiresAt = Instant.now().plus(5, ChronoUnit.MINUTES);

        return new RegisterResponse(savedUser.getId(), savedUser.getStatus().name(), otpExpiresAt);
    }
}