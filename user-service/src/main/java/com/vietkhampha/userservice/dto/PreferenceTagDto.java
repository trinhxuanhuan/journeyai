package com.vietkhampha.userservice.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;

public class PreferenceTagDto {

    @NotBlank(message = "tagCode khong duoc de trong")
    private String tagCode;

    @DecimalMin(value = "0.0", message = "weight phai tu 0.0 den 1.0")
    @DecimalMax(value = "1.0", message = "weight phai tu 0.0 den 1.0")
    private BigDecimal weight = BigDecimal.ONE;

    public PreferenceTagDto() {
    }

    public String getTagCode() { return tagCode; }
    public void setTagCode(String tagCode) { this.tagCode = tagCode; }

    public BigDecimal getWeight() { return weight; }
    public void setWeight(BigDecimal weight) { this.weight = weight; }
}