package com.vietkhampha.bookingservice.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    SLOT_NOT_FOUND(HttpStatus.NOT_FOUND, "Khong tim thay ngay khoi hanh"),
    SLOT_ALREADY_EXISTS(HttpStatus.CONFLICT, "Ngay khoi hanh nay da ton tai cho tour"),
    TOUR_SLOT_NOT_FOUND(HttpStatus.NOT_FOUND, "Khong tim thay ngay khoi hanh cho tour nay"),
    SLOT_UNAVAILABLE(HttpStatus.CONFLICT, "Rat tiec, khong du cho trong cho so luong yeu cau"),
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "Du lieu khong hop le"),
    INVALID_STATE_TRANSITION(HttpStatus.CONFLICT, "Khong the chuyen trang thai booking nhu yeu cau"),
    BOOKING_NOT_FOUND(HttpStatus.NOT_FOUND, "Khong tim thay booking");


    private final HttpStatus httpStatus;
    private final String defaultMessage;

    ErrorCode(HttpStatus httpStatus, String defaultMessage) {
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus getHttpStatus() { return httpStatus; }
    public String getDefaultMessage() { return defaultMessage; }
}