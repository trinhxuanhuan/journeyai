package com.vietkhampha.bookingservice.entity;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public class IdempotencyKeyId implements Serializable {

    private UUID customerId;
    private String key;

    protected IdempotencyKeyId() {
    }

    public IdempotencyKeyId(UUID customerId, String key) {
        this.customerId = customerId;
        this.key = key;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public String getKey() {
        return key;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof IdempotencyKeyId that)) {
            return false;
        }
        return Objects.equals(customerId, that.customerId) && Objects.equals(key, that.key);
    }

    @Override
    public int hashCode() {
        return Objects.hash(customerId, key);
    }
}
