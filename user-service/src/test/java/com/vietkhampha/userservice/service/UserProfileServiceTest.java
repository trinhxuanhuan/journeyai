package com.vietkhampha.userservice.service;

import com.vietkhampha.userservice.dto.PreferenceTagDto;
import com.vietkhampha.userservice.dto.ProfileResponse;
import com.vietkhampha.userservice.dto.UpdateProfileRequest;
import com.vietkhampha.userservice.entity.UserPreferenceTag;
import com.vietkhampha.userservice.entity.UserProfile;
import com.vietkhampha.userservice.event.UserEventPublisher;
import com.vietkhampha.userservice.exception.BusinessException;
import com.vietkhampha.userservice.exception.ErrorCode;
import com.vietkhampha.userservice.repository.UserProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserProfileServiceTest {

    @Mock private UserProfileRepository userProfileRepository;
    @Mock private UserEventPublisher userEventPublisher;

    private UserProfileService userProfileService;

    @BeforeEach
    void setUp() {
        userProfileService = new UserProfileService(userProfileRepository, userEventPublisher);
    }

    @Test
    void updateProfileNormalizesContactAvatarAndPreferenceCodes() {
        UUID userId = UUID.randomUUID();
        UserProfile profile = new UserProfile(userId);
        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setPhone("0912345678");
        request.setAvatarUrl("  https://cdn.example.com/avatar.jpg  ");
        request.setPreferenceTags(List.of(
                preference("food", "0.8"),
                preference("BEACH", "1.0")
        ));
        when(userProfileRepository.findByAuthUserId(userId)).thenReturn(Optional.of(profile));
        when(userProfileRepository.save(any(UserProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProfileResponse response = userProfileService.updateProfile(userId, request);

        assertThat(response.getPhone()).isEqualTo("0912345678");
        assertThat(response.getAvatarUrl()).isEqualTo("https://cdn.example.com/avatar.jpg");
        assertThat(response.getPreferenceTags())
                .extracting(PreferenceTagDto::getTagCode)
                .containsExactly("BEACH", "FOOD");
        verify(userEventPublisher).publishPreferencesUpdated(any(), any());
    }

    @Test
    void updateProfileCanClearOptionalContactAndAvatarWithEmptyStrings() {
        UUID userId = UUID.randomUUID();
        UserProfile profile = new UserProfile(userId);
        profile.updateProfile("0912345678", "https://cdn.example.com/avatar.jpg");
        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setPhone("");
        request.setAvatarUrl("");
        when(userProfileRepository.findByAuthUserId(userId)).thenReturn(Optional.of(profile));

        ProfileResponse response = userProfileService.updateProfile(userId, request);

        assertThat(response.getPhone()).isNull();
        assertThat(response.getAvatarUrl()).isNull();
        verify(userEventPublisher, never()).publishPreferencesUpdated(any(), any());
    }

    @Test
    void updateProfileRejectsDuplicatePreferencesAfterNormalization() {
        UUID userId = UUID.randomUUID();
        UserProfile profile = new UserProfile(userId);
        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setPreferenceTags(List.of(
                preference("food", "0.8"),
                preference("FOOD", "1.0")
        ));
        when(userProfileRepository.findByAuthUserId(userId)).thenReturn(Optional.of(profile));

        assertThatThrownBy(() -> userProfileService.updateProfile(userId, request))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.DUPLICATE_PREFERENCE)
                );
        verify(userProfileRepository, never()).save(any());
        verify(userEventPublisher, never()).publishPreferencesUpdated(any(), any());
    }

    @Test
    void getProfileReturnsDomainNotFoundForMissingProjection() {
        UUID userId = UUID.randomUUID();
        when(userProfileRepository.findByAuthUserId(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userProfileService.getMyProfile(userId))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PROFILE_NOT_FOUND)
                );
    }

    private PreferenceTagDto preference(String code, String weight) {
        PreferenceTagDto preference = new PreferenceTagDto();
        preference.setTagCode(code);
        preference.setWeight(new BigDecimal(weight));
        return preference;
    }
}
