package com.vietkhampha.notificationservice.controller;

import com.vietkhampha.notificationservice.dto.MarkAllReadResponse;
import com.vietkhampha.notificationservice.dto.NotificationPageResponse;
import com.vietkhampha.notificationservice.dto.NotificationPreferenceResponse;
import com.vietkhampha.notificationservice.dto.NotificationResponse;
import com.vietkhampha.notificationservice.dto.UnreadCountResponse;
import com.vietkhampha.notificationservice.dto.UpdateNotificationPreferenceRequest;
import com.vietkhampha.notificationservice.exception.BusinessException;
import com.vietkhampha.notificationservice.exception.ErrorCode;
import com.vietkhampha.notificationservice.service.NotificationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/v1/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public ResponseEntity<NotificationPageResponse> list(
            @RequestHeader("X-User-Id") String userIdHeader,
            @RequestParam(defaultValue = "ALL") String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(notificationService.list(userId(userIdHeader), status, page, size));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<UnreadCountResponse> unreadCount(
            @RequestHeader("X-User-Id") String userIdHeader
    ) {
        return ResponseEntity.ok(new UnreadCountResponse(notificationService.unreadCount(userId(userIdHeader))));
    }

    @PatchMapping("/{notificationId}/read")
    public ResponseEntity<NotificationResponse> markRead(
            @RequestHeader("X-User-Id") String userIdHeader,
            @PathVariable UUID notificationId
    ) {
        return ResponseEntity.ok(notificationService.markRead(userId(userIdHeader), notificationId));
    }

    @PatchMapping("/read-all")
    public ResponseEntity<MarkAllReadResponse> markAllRead(
            @RequestHeader("X-User-Id") String userIdHeader
    ) {
        return ResponseEntity.ok(notificationService.markAllRead(userId(userIdHeader)));
    }

    @GetMapping("/preferences")
    public ResponseEntity<NotificationPreferenceResponse> getPreferences(
            @RequestHeader("X-User-Id") String userIdHeader
    ) {
        return ResponseEntity.ok(notificationService.getPreferences(userId(userIdHeader)));
    }

    @PatchMapping("/preferences")
    public ResponseEntity<NotificationPreferenceResponse> updatePreferences(
            @RequestHeader("X-User-Id") String userIdHeader,
            @Valid @RequestBody UpdateNotificationPreferenceRequest request
    ) {
        return ResponseEntity.ok(notificationService.updatePreferences(
                userId(userIdHeader), request.emailEnabled()));
    }

    private UUID userId(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.INVALID_USER_ID);
        }
    }
}
