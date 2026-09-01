package com.vietkhampha.userservice.controller;

import com.vietkhampha.userservice.dto.ProfileResponse;
import com.vietkhampha.userservice.exception.GlobalExceptionHandler;
import com.vietkhampha.userservice.service.UserProfileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
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

class UserProfileControllerTest {

    private UserProfileService userProfileService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        userProfileService = mock(UserProfileService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new UserProfileController(userProfileService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void getMeUsesTrustedGatewayIdentityHeader() throws Exception {
        UUID userId = UUID.randomUUID();
        when(userProfileService.getMyProfile(userId))
                .thenReturn(new ProfileResponse(userId, "0912345678", null, List.of()));

        mockMvc.perform(get("/v1/users/me").header("X-User-Id", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(userId.toString()))
                .andExpect(jsonPath("$.phone").value("0912345678"))
                .andExpect(jsonPath("$.preferenceTags").isArray());
    }

    @Test
    void patchMeRejectsUnsafeAvatarAndInvalidPhone() throws Exception {
        UUID userId = UUID.randomUUID();

        mockMvc.perform(patch("/v1/users/me")
                        .header("X-User-Id", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "phone": "123",
                                  "avatarUrl": "http://unsafe.example.com/avatar.jpg"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.phone").exists())
                .andExpect(jsonPath("$.fieldErrors.avatarUrl").exists());
    }

    @Test
    void patchMeAcceptsClearingOptionalFields() throws Exception {
        UUID userId = UUID.randomUUID();
        when(userProfileService.updateProfile(eq(userId), any()))
                .thenReturn(new ProfileResponse(userId, null, null, List.of()));

        mockMvc.perform(patch("/v1/users/me")
                        .header("X-User-Id", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"\",\"avatarUrl\":\"\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phone").doesNotExist())
                .andExpect(jsonPath("$.avatarUrl").doesNotExist());

        verify(userProfileService).updateProfile(eq(userId), any());
    }

    @Test
    void patchMeTrimsUserInputBeforeValidation() throws Exception {
        UUID userId = UUID.randomUUID();
        when(userProfileService.updateProfile(eq(userId), any()))
                .thenReturn(new ProfileResponse(
                        userId,
                        "0912345678",
                        "https://cdn.example.com/avatar.jpg",
                        List.of()
                ));

        mockMvc.perform(patch("/v1/users/me")
                        .header("X-User-Id", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "phone": " 0912345678 ",
                                  "avatarUrl": " https://cdn.example.com/avatar.jpg ",
                                  "preferenceTags": [
                                    { "tagCode": " food ", "weight": 0.8 }
                                  ]
                                }
                                """))
                .andExpect(status().isOk());

        verify(userProfileService).updateProfile(eq(userId), any());
    }
}
