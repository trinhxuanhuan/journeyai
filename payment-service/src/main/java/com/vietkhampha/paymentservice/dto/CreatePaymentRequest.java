package com.vietkhampha.paymentservice.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.UUID;

public class CreatePaymentRequest {
    @NotNull(message = "bookingId khong duoc de trong")
    private UUID bookingId;

    @NotNull(message = "gateway khong duoc de trong")
    @Pattern(regexp = "VNPAY", message = "Gateway hien tai chi ho tro VNPAY")
    private String gateway = "VNPAY";

    protected CreatePaymentRequest() {}

    public UUID getBookingId() { return bookingId; }
    public void setBookingId(UUID bookingId) { this.bookingId = bookingId; }
    public String getGateway() { return gateway; }
    public void setGateway(String gateway) { this.gateway = gateway; }
}
