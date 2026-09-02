package com.vietkhampha.authservice.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy tài khoản"),
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "Dữ liệu không hợp lệ"),
    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "Email đã được sử dụng"),
    OTP_INVALID(HttpStatus.BAD_REQUEST, "Mã OTP không đúng"),
    OTP_EXPIRED(HttpStatus.CONFLICT, "Mã OTP đã hết hạn"),
    OTP_ATTEMPTS_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "Đã nhập sai OTP quá 5 lần, vui lòng thử lại sau 15 phút"),
    OTP_RESEND_COOLDOWN(HttpStatus.TOO_MANY_REQUESTS, "Vui lòng chờ 60 giây trước khi yêu cầu mã mới"),
    OTP_SEND_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "Bạn đã yêu cầu quá nhiều mã xác thực, vui lòng thử lại sau"),
    OTP_RESEND_NOT_ALLOWED(HttpStatus.CONFLICT, "Không thể gửi mã xác thực cho tài khoản này"),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Email hoặc mật khẩu không đúng"),
    ACCOUNT_LOCKED(HttpStatus.FORBIDDEN, "Tài khoản bị khóa tạm 15 phút do đăng nhập sai quá nhiều lần"),
    ACCOUNT_SUSPENDED(HttpStatus.FORBIDDEN, "Tài khoản đã bị khóa bởi quản trị viên"),
    ACCOUNT_UNVERIFIED(HttpStatus.FORBIDDEN, "Tài khoản chưa xác thực OTP"),
    REFRESH_TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "Refresh token không hợp lệ"),
    REFRESH_TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "Refresh token đã hết hạn, vui lòng đăng nhập lại");


    private final HttpStatus httpStatus;
    private final String defaultMessage;

    ErrorCode(HttpStatus httpStatus, String defaultMessage) {
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus getHttpStatus() { return httpStatus; }
    public String getDefaultMessage() { return defaultMessage; }
}
