package com.vietkhampha.notificationservice.dto;

import jakarta.validation.constraints.NotNull;

public record UpdateNotificationPreferenceRequest(
        @NotNull(message = "Vui lòng chọn bật hoặc tắt thông báo email") Boolean emailEnabled
) {
}
