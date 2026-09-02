package com.vietkhampha.authservice.dto;

import jakarta.validation.constraints.NotBlank;

public class RefreshTokenRequest {

    @NotBlank(message = "refreshToken không được để trống")
    private String refreshToken;

    protected RefreshTokenRequest() {
    }

    public String getRefreshToken() { return refreshToken; }
    public void setRefreshToken(String refreshToken) { this.refreshToken = refreshToken; }
}
