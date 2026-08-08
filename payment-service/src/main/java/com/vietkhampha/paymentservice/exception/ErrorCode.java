package com.vietkhampha.paymentservice.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    BOOKING_NOT_PENDING(HttpStatus.UNPROCESSABLE_ENTITY, "Booking khong o trang thai cho thanh toan hoac da het han giu cho"),
    PAYMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "Khong tim thay giao dich"),
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "Du lieu khong hop le"),
    WEBHOOK_INVALID_CHECKSUM(HttpStatus.BAD_REQUEST, "Chu ky khong hop le"),
    PAYMENT_ALREADY_INITIATED(HttpStatus.CONFLICT, "Da co giao dich dang cho xu ly cho booking nay, vui long hoan tat hoac doi het han");

    private final HttpStatus httpStatus;
    private final String defaultMessage;

    ErrorCode(HttpStatus httpStatus, String defaultMessage) {
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus getHttpStatus() { return httpStatus; }
    public String getDefaultMessage() { return defaultMessage; }
}