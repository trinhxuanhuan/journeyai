package com.vietkhampha.paymentservice.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

public class CreatePaymentResponse {
    private UUID paymentId;
    private String redirectUrl;

    @JsonCreator
    public CreatePaymentResponse(
            @JsonProperty("paymentId") UUID paymentId,
            @JsonProperty("redirectUrl") String redirectUrl
    ) {
        this.paymentId = paymentId;
        this.redirectUrl = redirectUrl;
    }

    public UUID getPaymentId() { return paymentId; }
    public String getRedirectUrl() { return redirectUrl; }
}
