package com.vietkhampha.notificationservice.service;

import com.vietkhampha.notificationservice.dto.MarkAllReadResponse;
import com.vietkhampha.notificationservice.dto.NotificationPageResponse;
import com.vietkhampha.notificationservice.dto.NotificationPreferenceResponse;
import com.vietkhampha.notificationservice.dto.NotificationResponse;
import com.vietkhampha.notificationservice.entity.Notification;
import com.vietkhampha.notificationservice.entity.NotificationEmailDelivery;
import com.vietkhampha.notificationservice.entity.NotificationRecipient;
import com.vietkhampha.notificationservice.exception.BusinessException;
import com.vietkhampha.notificationservice.exception.ErrorCode;
import com.vietkhampha.notificationservice.repository.NotificationEmailDeliveryRepository;
import com.vietkhampha.notificationservice.repository.NotificationRepository;
import com.vietkhampha.notificationservice.repository.NotificationRecipientRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
public class NotificationService {

    public record NotificationDraft(
            UUID eventId,
            UUID authUserId,
            String type,
            Notification.Category category,
            String title,
            String message,
            String actionUrl,
            String referenceType,
            String referenceId,
            boolean emailImportant
    ) {
    }

    private final NotificationRepository notificationRepository;
    private final NotificationEmailDeliveryRepository emailDeliveryRepository;
    private final NotificationRecipientRepository recipientRepository;
    private final boolean emailEnabled;
    private final String frontendBaseUrl;

    public NotificationService(
            NotificationRepository notificationRepository,
            NotificationEmailDeliveryRepository emailDeliveryRepository,
            NotificationRecipientRepository recipientRepository,
            @Value("${app.notification.email.enabled:false}") boolean emailEnabled,
            @Value("${app.frontend.base-url:http://localhost:3000}") String frontendBaseUrl
    ) {
        this.notificationRepository = notificationRepository;
        this.emailDeliveryRepository = emailDeliveryRepository;
        this.recipientRepository = recipientRepository;
        this.emailEnabled = emailEnabled;
        this.frontendBaseUrl = stripTrailingSlash(frontendBaseUrl);
    }

    @Transactional(readOnly = true)
    public NotificationPageResponse list(UUID authUserId, String rawStatus, int page, int size) {
        validatePage(page, size);
        String status = rawStatus == null ? "ALL" : rawStatus.trim().toUpperCase(Locale.ROOT);
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        Page<Notification> result = switch (status) {
            case "ALL" -> notificationRepository.findByAuthUserId(authUserId, pageable);
            case "UNREAD" -> notificationRepository.findByAuthUserIdAndReadAtIsNull(authUserId, pageable);
            case "READ" -> notificationRepository.findByAuthUserIdAndReadAtIsNotNull(authUserId, pageable);
            default -> throw new BusinessException(ErrorCode.INVALID_NOTIFICATION_FILTER);
        };

        return new NotificationPageResponse(
                result.getContent().stream().map(NotificationResponse::from).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages(),
                notificationRepository.countByAuthUserIdAndReadAtIsNull(authUserId)
        );
    }

    @Transactional(readOnly = true)
    public long unreadCount(UUID authUserId) {
        return notificationRepository.countByAuthUserIdAndReadAtIsNull(authUserId);
    }

    @Transactional
    public NotificationResponse markRead(UUID authUserId, UUID notificationId) {
        Notification notification = notificationRepository.findByIdAndAuthUserId(notificationId, authUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOTIFICATION_NOT_FOUND));
        notification.markRead(Instant.now());
        return NotificationResponse.from(notificationRepository.save(notification));
    }

    @Transactional
    public MarkAllReadResponse markAllRead(UUID authUserId) {
        return new MarkAllReadResponse(notificationRepository.markAllRead(authUserId, Instant.now()));
    }

    @Transactional(readOnly = true)
    public NotificationPreferenceResponse getPreferences(UUID authUserId) {
        return new NotificationPreferenceResponse(recipientRepository.findById(authUserId)
                .map(NotificationRecipient::isEmailNotificationsEnabled)
                .orElse(true));
    }

    @Transactional
    public NotificationPreferenceResponse updatePreferences(UUID authUserId, boolean emailNotificationsEnabled) {
        NotificationRecipient profile = recipientRepository.findById(authUserId)
                .orElseGet(() -> new NotificationRecipient(authUserId));
        profile.updateEmailNotificationsEnabled(emailNotificationsEnabled);
        recipientRepository.save(profile);
        return new NotificationPreferenceResponse(profile.isEmailNotificationsEnabled());
    }

    @Transactional
    public Notification create(NotificationDraft draft) {
        Optional<Notification> existing = notificationRepository.findByEventId(draft.eventId());
        if (existing.isPresent()) return existing.get();

        Notification notification = notificationRepository.saveAndFlush(new Notification(
                draft.authUserId(), draft.eventId(), draft.type(), draft.category(),
                draft.title(), draft.message(), draft.actionUrl(),
                draft.referenceType(), draft.referenceId()
        ));
        if (draft.emailImportant()) createEmailDelivery(notification);
        return notification;
    }

    private void createEmailDelivery(Notification notification) {
        String subject = "[Việt Khám Phá] " + notification.getTitle();
        Optional<NotificationRecipient> profile = recipientRepository.findById(notification.getAuthUserId());
        String greetingName = profile.map(NotificationRecipient::getFullName)
                .filter(name -> !name.isBlank())
                .orElse("bạn");
        String body = "Xin chào " + greetingName + ",\n\n"
                + notification.getMessage() + "\n\n"
                + "Xem chi tiết: " + frontendBaseUrl + notification.getActionUrl() + "\n\n"
                + "Trân trọng,\nViệt Khám Phá — Biến Việt Nam thành một hành trình đẹp.";

        NotificationEmailDelivery delivery;
        if (!emailEnabled) {
            delivery = NotificationEmailDelivery.skipped(
                    notification, subject, body, "Kênh email đang tắt theo cấu hình hệ thống");
        } else if (profile.isPresent() && !profile.get().isEmailNotificationsEnabled()) {
            delivery = NotificationEmailDelivery.skipped(
                    notification, subject, body, "Người dùng đã tắt thông báo email");
        } else if (profile.isEmpty() || profile.get().getEmail() == null || profile.get().getEmail().isBlank()) {
            delivery = NotificationEmailDelivery.waitingForRecipient(notification, subject, body, Instant.now());
        } else {
            delivery = NotificationEmailDelivery.pending(
                    notification, profile.get().getEmail(), subject, body, Instant.now());
        }
        emailDeliveryRepository.save(delivery);
    }

    private void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new BusinessException(ErrorCode.INVALID_PAGINATION);
        }
    }

    private static String stripTrailingSlash(String value) {
        if (value == null || value.isBlank()) return "http://localhost:3000";
        String normalized = value.trim();
        while (normalized.endsWith("/")) normalized = normalized.substring(0, normalized.length() - 1);
        return normalized;
    }
}
