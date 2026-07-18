package com.vietkhampha.authservice.exception;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Map<String, String>> handleBusinessException(BusinessException ex) {
        return ResponseEntity
                .status(ex.getErrorCode().getHttpStatus())
                .body(Map.of("error", ex.getErrorCode().name(), "message", ex.getMessage()));
    }
}