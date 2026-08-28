package com.vietkhampha.bookingservice.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    SLOT_NOT_FOUND(HttpStatus.NOT_FOUND, "Khong tim thay ngay khoi hanh"),
    SLOT_ALREADY_EXISTS(HttpStatus.CONFLICT, "Ngay khoi hanh nay da ton tai cho tour"),
    TOUR_NOT_AVAILABLE(HttpStatus.NOT_FOUND, "Tour khong ton tai hoac khong con hoat dong"),
    TOUR_SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "Tam thoi khong the xac minh thong tin tour"),
    TOUR_SERVICE_INVALID_RESPONSE(HttpStatus.BAD_GATEWAY, "Tour Service tra ve du lieu khong hop le"),
    GUIDE_NOT_AVAILABLE(HttpStatus.BAD_REQUEST, "Huong dan vien khong ton tai hoac khong con hoat dong"),
    DEPARTURE_CONFIGURATION_INVALID(HttpStatus.BAD_REQUEST, "Cau hinh lan khoi hanh khong hop le"),
    TOUR_SLOT_NOT_FOUND(HttpStatus.NOT_FOUND, "Khong tim thay ngay khoi hanh cho tour nay"),
    SLOT_UNAVAILABLE(HttpStatus.CONFLICT, "Rat tiec, khong du cho trong cho so luong yeu cau"),
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "Du lieu khong hop le"),
    INVALID_STATE_TRANSITION(HttpStatus.CONFLICT, "Khong the chuyen trang thai booking nhu yeu cau"),
    IDEMPOTENCY_KEY_INVALID(HttpStatus.BAD_REQUEST, "Idempotency-Key phai co tu 1 den 255 ky tu khong phai khoang trang"),
    IDEMPOTENCY_KEY_REUSED(HttpStatus.CONFLICT, "Idempotency-Key da duoc dung voi noi dung booking khac"),
    IDEMPOTENCY_KEY_EXPIRED(HttpStatus.CONFLICT, "Idempotency-Key da het han; hay gui lai voi key moi"),
    PAGINATION_INVALID(HttpStatus.BAD_REQUEST, "page phai >= 0 va size phai trong khoang 1-100"),
    BOOKING_NOT_FOUND(HttpStatus.NOT_FOUND, "Khong tim thay booking"),
    BOOKING_REQUEST_INVALID(HttpStatus.BAD_REQUEST, "Thong tin dat tour khong hop le"),
    GROUP_SIZE_INVALID(HttpStatus.UNPROCESSABLE_ENTITY, "So luong khach khong nam trong gioi han cua tour"),
    PRIVATE_START_DATE_INVALID(HttpStatus.UNPROCESSABLE_ENTITY, "Ngay khoi hanh tour rieng khong hop le"),
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
