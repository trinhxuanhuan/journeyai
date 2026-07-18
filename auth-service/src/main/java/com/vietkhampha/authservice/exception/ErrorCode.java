package com.vietkhampha.authservice.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "Email da duoc su dung"),
    OTP_INVALID(HttpStatus.BAD_REQUEST, "Ma OTP khong dung"),
    OTP_EXPIRED(HttpStatus.CONFLICT, "Ma OTP da het han"),
    OTP_ATTEMPTS_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "Da nhap sai OTP qua 5 lan, vui long thu lai sau 15 phut"),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Email hoac mat khau khong dung"),
    ACCOUNT_LOCKED(HttpStatus.FORBIDDEN, "Tai khoan bi khoa tam 15 phut do dang nhap sai qua nhieu lan"),
    ACCOUNT_SUSPENDED(HttpStatus.FORBIDDEN, "Tai khoan da bi khoa boi quan tri vien"),
    ACCOUNT_UNVERIFIED(HttpStatus.FORBIDDEN, "Tai khoan chua xac thuc OTP"),
    REFRESH_TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "Refresh token khong hop le"),
    REFRESH_TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "Refresh token da het han, vui long dang nhap lai");


    private final HttpStatus httpStatus;
    private final String defaultMessage;

    ErrorCode(HttpStatus httpStatus, String defaultMessage) {
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus getHttpStatus() { return httpStatus; }
    public String getDefaultMessage() { return defaultMessage; }
}