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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class UserProfileService {

    private final UserProfileRepository userProfileRepository;
    private final UserEventPublisher userEventPublisher;

    public UserProfileService(UserProfileRepository userProfileRepository, UserEventPublisher userEventPublisher) {
        this.userProfileRepository = userProfileRepository;
        this.userEventPublisher = userEventPublisher;
    }

    @Transactional(readOnly = true)
    public ProfileResponse getMyProfile(UUID authUserId) {
        UserProfile profile = findProfileOrThrow(authUserId);
        return toResponse(profile);
    }

    @Transactional
    public ProfileResponse updateProfile(UUID authUserId, UpdateProfileRequest request) {
        UserProfile profile = findProfileOrThrow(authUserId);

        profile.updateProfile(request.getPhone(), request.getAvatarUrl());

        // Chỉ thay tag + publish event khi client THỰC SỰ gửi field này —
        // null nghĩa là "không đổi phần tag", tránh xóa nhầm tag cũ khi
        // client chỉ muốn cập nhật avatar/SĐT (đúng ngữ nghĩa PATCH).
        if (request.getPreferenceTags() != null) {
            Set<String> uniqueTagCodes = new HashSet<>();
            List<PreferenceTagDto> normalizedTags = request.getPreferenceTags().stream()
                    .map(dto -> normalizePreference(dto, uniqueTagCodes))
                    .toList();
            List<UserPreferenceTag> newTags = normalizedTags.stream()
                    .map(dto -> new UserPreferenceTag(dto.getTagCode(), dto.getWeight()))
                    .collect(Collectors.toList());
            profile.replacePreferenceTags(newTags);

            List<Map<String, Object>> eventPayloadTags = normalizedTags.stream()
                    .map(dto -> Map.<String, Object>of("tagCode", dto.getTagCode(), "weight", dto.getWeight()))
                    .collect(Collectors.toList());
            userEventPublisher.publishPreferencesUpdated(authUserId, eventPayloadTags);
        }

        userProfileRepository.save(profile);
        return toResponse(profile);
    }

    private PreferenceTagDto normalizePreference(PreferenceTagDto source, Set<String> uniqueTagCodes) {
        String normalizedCode = source.getTagCode().trim().toUpperCase(Locale.ROOT);
        if (!uniqueTagCodes.add(normalizedCode)) {
            throw new BusinessException(ErrorCode.DUPLICATE_PREFERENCE);
        }

        PreferenceTagDto normalized = new PreferenceTagDto();
        normalized.setTagCode(normalizedCode);
        normalized.setWeight(source.getWeight());
        return normalized;
    }

    private UserProfile findProfileOrThrow(UUID authUserId) {
        return userProfileRepository.findByAuthUserId(authUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROFILE_NOT_FOUND));
    }

    private ProfileResponse toResponse(UserProfile profile) {
        List<PreferenceTagDto> tags = profile.getPreferenceTags().stream()
                .sorted(java.util.Comparator.comparing(UserPreferenceTag::getTagCode))
                .map(tag -> {
                    PreferenceTagDto dto = new PreferenceTagDto();
                    dto.setTagCode(tag.getTagCode());
                    dto.setWeight(tag.getWeight());
                    return dto;
                })
                .collect(Collectors.toList());

        return new ProfileResponse(profile.getAuthUserId(), profile.getPhone(), profile.getAvatarUrl(), tags);
    }
}
