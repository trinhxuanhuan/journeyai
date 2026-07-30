package com.vietkhampha.paymentservice.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    BOOKING_NOT_PENDING(HttpStatus.UNPROCESSABLE_ENTITY, "Booking khong o trang thai cho thanh toan hoac da het han giu cho"),
    PAYMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "Khong tim thay giao dich"),
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