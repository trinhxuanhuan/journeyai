package com.vietkhampha.bookingservice.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    SLOT_NOT_FOUND(HttpStatus.NOT_FOUND, "Khong tim thay ngay khoi hanh"),
    SLOT_ALREADY_EXISTS(HttpStatus.CONFLICT, "Ngay khoi hanh nay da ton tai cho tour"),
    TOUR_SLOT_NOT_FOUND(HttpStatus.NOT_FOUND, "Khong tim thay ngay khoi hanh cho tour nay"),
    SLOT_UNAVAILABLE(HttpStatus.CONFLICT, "Rat tiec, khong du cho trong cho so luong yeu cau"),
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "Du lieu khong hop le"),
    INVALID_STATE_TRANSITION(HttpStatus.CONFLICT, "Khong the chuyen trang thai booking nhu yeu cau"),
    IDEMPOTENCY_KEY_INVALID(HttpStatus.BAD_REQUEST, "Idempotency-Key phai co tu 1 den 255 ky tu khong phai khoang trang"),
    IDEMPOTENCY_KEY_REUSED(HttpStatus.CONFLICT, "Idempotency-Key da duoc dung voi noi dung booking khac"),
    IDEMPOTENCY_KEY_EXPIRED(HttpStatus.CONFLICT, "Idempotency-Key da het han; hay gui lai voi key moi"),
    BOOKING_NOT_FOUND(HttpStatus.NOT_FOUND, "Khong tim thay booking"),
    BOOKING_NOT_CANCELLABLE(HttpStatus.UNPROCESSABLE_ENTITY, "Booking khong o trang thai cho phep huy"),
    BOOKING_CANCEL_WINDOW_CLOSED(HttpStatus.UNPROCESSABLE_ENTITY, "Da qua thoi han huy (trong vong 24 gio truoc khoi hanh)");


    private final HttpStatus httpStatus;
    private final String defaultMessage;

    ErrorCode(HttpStatus httpStatus, String defaultMessage) {
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus getHttpStatus() { return httpStatus; }
    public String getDefaultMessage() { return defaultMessage; }
}
