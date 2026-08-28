package com.vietkhampha.notificationservice.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    NOTIFICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy thông báo"),
    INVALID_PAGINATION(HttpStatus.BAD_REQUEST, "Thông tin phân trang không hợp lệ"),
    INVALID_NOTIFICATION_FILTER(HttpStatus.BAD_REQUEST, "Bộ lọc trạng thái thông báo không hợp lệ"),
    INVALID_USER_ID(HttpStatus.UNAUTHORIZED, "Danh tính người dùng không hợp lệ"),
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "Dữ liệu không hợp lệ");

    private final HttpStatus httpStatus;
    private final String defaultMessage;

    ErrorCode(HttpStatus httpStatus, String defaultMessage) {
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus getHttpStatus() { return httpStatus; }
    public String getDefaultMessage() { return defaultMessage; }
}
