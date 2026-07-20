package com.vietkhampha.bookingservice.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    SLOT_NOT_FOUND(HttpStatus.NOT_FOUND, "Khong tim thay ngay khoi hanh"),
    SLOT_ALREADY_EXISTS(HttpStatus.CONFLICT, "Ngay khoi hanh nay da ton tai cho tour"),
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