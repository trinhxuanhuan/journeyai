package com.vietkhampha.userservice.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    PROFILE_NOT_FOUND(HttpStatus.NOT_FOUND, "Khong tim thay ho so nguoi dung"),
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "Du lieu khong hop le");

    private final HttpStatus httpStatus;
    private final String defaultMessage;

    ErrorCode(HttpStatus httpStatus, String defaultMessage) {
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus getHttpStatus() { return httpStatus; }
    public String getDefaultMessage() { return defaultMessage; }
}
