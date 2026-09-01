package com.vietkhampha.userservice.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    PROFILE_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy hồ sơ người dùng"),
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "Dữ liệu không hợp lệ"),
    DUPLICATE_PREFERENCE(HttpStatus.BAD_REQUEST, "Danh sách sở thích không được chứa mã trùng lặp");

    private final HttpStatus httpStatus;
    private final String defaultMessage;

    ErrorCode(HttpStatus httpStatus, String defaultMessage) {
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus getHttpStatus() { return httpStatus; }
    public String getDefaultMessage() { return defaultMessage; }
}
