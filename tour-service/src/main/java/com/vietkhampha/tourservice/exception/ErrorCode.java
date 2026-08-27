package com.vietkhampha.tourservice.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    TOUR_NOT_FOUND(HttpStatus.NOT_FOUND, "Khong tim thay tour"),
    TOUR_GUIDE_NOT_FOUND(HttpStatus.BAD_REQUEST, "Huong dan vien khong ton tai"),
    TOUR_CONFIGURATION_INVALID(HttpStatus.BAD_REQUEST, "Cau hinh tour khong hop le"),
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
