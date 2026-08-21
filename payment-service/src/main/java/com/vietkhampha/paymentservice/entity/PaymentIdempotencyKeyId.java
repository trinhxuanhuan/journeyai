package com.vietkhampha.paymentservice.entity;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public class PaymentIdempotencyKeyId implements Serializable {

    private UUID customerId;
    private String key;

    public PaymentIdempotencyKeyId() {
    }

    public PaymentIdempotencyKeyId(UUID customerId, String key) {
        this.customerId = customerId;
        this.key = key;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof PaymentIdempotencyKeyId that)) {
            return false;
        }
        return Objects.equals(customerId, that.customerId) && Objects.equals(key, that.key);
    }

    @Override
    public int hashCode() {
        return Objects.hash(customerId, key);
    }
}
