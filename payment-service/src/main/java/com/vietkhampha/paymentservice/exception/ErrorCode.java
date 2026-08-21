package com.vietkhampha.paymentservice.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    BOOKING_NOT_FOUND(HttpStatus.NOT_FOUND, "Khong tim thay booking"),
    BOOKING_NOT_PENDING(HttpStatus.UNPROCESSABLE_ENTITY, "Booking khong o trang thai cho thanh toan hoac da het han giu cho"),
    PAYMENT_WINDOW_EXPIRED(HttpStatus.CONFLICT, "Thoi gian giu cho da het, khong the khoi tao thanh toan"),
    PAYMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "Khong tim thay giao dich"),
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "Du lieu khong hop le"),
    WEBHOOK_INVALID_CHECKSUM(HttpStatus.BAD_REQUEST, "Chu ky khong hop le"),
    PAYMENT_ALREADY_INITIATED(HttpStatus.CONFLICT, "Da co giao dich dang cho xu ly cho booking nay, vui long hoan tat hoac doi het han"),
    IDEMPOTENCY_KEY_INVALID(HttpStatus.BAD_REQUEST, "Idempotency-Key khong hop le"),
    IDEMPOTENCY_KEY_REUSED(HttpStatus.CONFLICT, "Idempotency-Key da duoc su dung voi noi dung khac"),
    IDEMPOTENCY_KEY_EXPIRED(HttpStatus.CONFLICT, "Idempotency-Key da het thoi gian replay");

    private final HttpStatus httpStatus;
    private final String defaultMessage;

    ErrorCode(HttpStatus httpStatus, String defaultMessage) {
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus getHttpStatus() { return httpStatus; }
    public String getDefaultMessage() { return defaultMessage; }
}
