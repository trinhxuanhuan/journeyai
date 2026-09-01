package com.vietkhampha.authservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class UpdateCurrentUserRequest {

    @NotBlank(message = "Họ tên không được để trống")
    @Size(min = 2, max = 100, message = "Họ tên phải có từ 2 đến 100 ký tự")
    private String fullName;

    public UpdateCurrentUserRequest() {
    }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) {
        this.fullName = fullName == null ? null : fullName.trim().replaceAll("\\s+", " ");
    }
}
