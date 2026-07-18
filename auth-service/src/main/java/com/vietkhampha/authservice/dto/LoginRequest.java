package com.vietkhampha.authservice.dto;

import jakarta.validation.constraints.NotBlank;

public class LoginRequest {

    @NotBlank(message = "Email khong duoc de trong")
    private String email;

    @NotBlank(message = "Mat khau khong duoc de trong")
    private String password;

    protected LoginRequest() {
    }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}