package com.vietkhampha.userservice.controller;

import com.vietkhampha.userservice.dto.ProfileResponse;
import com.vietkhampha.userservice.dto.UpdateProfileRequest;
import com.vietkhampha.userservice.service.UserProfileService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;
@RestController
@RequestMapping("/v1/users")
public class UserProfileController {

    private final UserProfileService userProfileService;

    public UserProfileController(UserProfileService userProfileService) {
        this.userProfileService = userProfileService;
    }

    @GetMapping("/me")
    public ResponseEntity<ProfileResponse> getMyProfile(@RequestHeader("X-User-Id") String userIdHeader) {
        UUID authUserId = UUID.fromString(userIdHeader);
        return ResponseEntity.ok(userProfileService.getMyProfile(authUserId));
    }

    @PatchMapping("/me")
    public ResponseEntity<ProfileResponse> updateMyProfile(
            @RequestHeader("X-User-Id") String userIdHeader,
            @Valid @RequestBody UpdateProfileRequest request
    ) {
        UUID authUserId = UUID.fromString(userIdHeader);
        return ResponseEntity.ok(userProfileService.updateProfile(authUserId, request));
    }

}
