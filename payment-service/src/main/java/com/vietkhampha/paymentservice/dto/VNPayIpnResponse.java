package com.vietkhampha.paymentservice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record VNPayIpnResponse(
        @JsonProperty("RspCode") String responseCode,
        @JsonProperty("Message") String message
) {
    public static VNPayIpnResponse success() {
        return new VNPayIpnResponse("00", "Confirm Success");
    }

    public static VNPayIpnResponse orderNotFound() {
        return new VNPayIpnResponse("01", "Order not found");
    }

    public static VNPayIpnResponse alreadyConfirmed() {
        return new VNPayIpnResponse("02", "Order already confirmed");
    }

    public static VNPayIpnResponse invalidAmount() {
        return new VNPayIpnResponse("04", "Invalid amount");
    }

    public static VNPayIpnResponse invalidChecksum() {
        return new VNPayIpnResponse("97", "Invalid Checksum");
    }

    public static VNPayIpnResponse invalidRequest() {
        return new VNPayIpnResponse("99", "Invalid request");
    }
}
