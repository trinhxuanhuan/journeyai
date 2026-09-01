package com.vietkhampha.userservice.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;

public class PreferenceTagDto {

    @NotBlank(message = "Mã sở thích không được để trống")
    @Pattern(
            regexp = "^[A-Za-z][A-Za-z0-9_]{1,49}$",
            message = "Mã sở thích phải có từ 2 đến 50 ký tự chữ, số hoặc dấu gạch dưới"
    )
    private String tagCode;

    @NotNull(message = "Mức độ ưu tiên không được để trống")
    @DecimalMin(value = "0.0", message = "Mức độ ưu tiên phải từ 0.0 đến 1.0")
    @DecimalMax(value = "1.0", message = "Mức độ ưu tiên phải từ 0.0 đến 1.0")
    private BigDecimal weight = BigDecimal.ONE;

    public PreferenceTagDto() {
    }

    public String getTagCode() { return tagCode; }
    public void setTagCode(String tagCode) { this.tagCode = tagCode == null ? null : tagCode.trim(); }

    public BigDecimal getWeight() { return weight; }
    public void setWeight(BigDecimal weight) { this.weight = weight; }
}
