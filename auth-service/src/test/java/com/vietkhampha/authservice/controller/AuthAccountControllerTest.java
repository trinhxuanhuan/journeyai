package com.vietkhampha.authservice.controller;

import com.vietkhampha.authservice.dto.CurrentUserResponse;
import com.vietkhampha.authservice.exception.GlobalExceptionHandler;
import com.vietkhampha.authservice.service.AuthService;
import com.vietkhampha.authservice.service.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthAccountControllerTest {

    private AuthService authService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        authService = mock(AuthService.class);
        AuthController controller = new AuthController(authService, mock(JwtService.class));
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void getMeUsesTrustedGatewayIdentityHeader() throws Exception {
        UUID userId = UUID.randomUUID();
        when(authService.getCurrentUser(userId)).thenReturn(response(userId, "Nguyễn An"));

        mockMvc.perform(get("/v1/auth/me").header("X-User-Id", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(userId.toString()))
                .andExpect(jsonPath("$.email").value("khach@example.com"))
                .andExpect(jsonPath("$.fullName").value("Nguyễn An"))
                .andExpect(jsonPath("$.role").value("CUSTOMER"));
    }

    @Test
    void patchMeRejectsBlankFullNameBeforeCallingService() throws Exception {
        UUID userId = UUID.randomUUID();

        mockMvc.perform(patch("/v1/auth/me")
                        .header("X-User-Id", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.fullName").exists());
    }

    @Test
    void patchMeValidatesFullNameAfterWhitespaceNormalization() throws Exception {
        UUID userId = UUID.randomUUID();

        mockMvc.perform(patch("/v1/auth/me")
                        .header("X-User-Id", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"  A  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.fullName").exists());
    }

    @Test
    void patchMeReturnsUpdatedIdentity() throws Exception {
        UUID userId = UUID.randomUUID();
        when(authService.updateCurrentUser(eq(userId), any())).thenReturn(response(userId, "Tên mới"));

        mockMvc.perform(patch("/v1/auth/me")
                        .header("X-User-Id", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Tên mới\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Tên mới"));

        verify(authService).updateCurrentUser(eq(userId), any());
    }

    private CurrentUserResponse response(UUID userId, String fullName) {
        Instant now = Instant.parse("2026-09-01T00:00:00Z");
        return new CurrentUserResponse(
                userId,
                "khach@example.com",
                fullName,
                "CUSTOMER",
                "ACTIVE",
                now,
                now
        );
    }
}
