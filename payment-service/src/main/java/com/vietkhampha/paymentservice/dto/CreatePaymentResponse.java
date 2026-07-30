package com.vietkhampha.paymentservice.dto;

import java.util.UUID;

public class CreatePaymentResponse {
    private UUID paymentId;
    private String redirectUrl;

    public CreatePaymentResponse(UUID paymentId, String redirectUrl) {
        this.paymentId = paymentId;
        this.redirectUrl = redirectUrl;
    }

    public UUID getPaymentId() { return paymentId; }
    public String getRedirectUrl() { return redirectUrl; }
}