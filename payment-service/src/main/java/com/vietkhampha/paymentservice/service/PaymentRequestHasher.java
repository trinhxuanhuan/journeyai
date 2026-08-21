package com.vietkhampha.paymentservice.service;

import com.vietkhampha.paymentservice.dto.CreatePaymentRequest;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;

@Component
public class PaymentRequestHasher {

    public static final String HASH_VERSION = "SHA256_V1";
    private static final String OPERATION = "POST:/v1/payments";

    public String hash(UUID customerId, CreatePaymentRequest request) {
        MessageDigest digest = sha256();
        append(digest, HASH_VERSION);
        append(digest, OPERATION);
        append(digest, customerId.toString());
        append(digest, request.getBookingId() == null ? null : request.getBookingId().toString());
        append(digest, canonicalGateway(request.getGateway()));
        return HexFormat.of().formatHex(digest.digest());
    }

    private String canonicalGateway(String gateway) {
        return gateway == null ? null : gateway.toUpperCase(Locale.ROOT);
    }

    private void append(MessageDigest digest, String value) {
        if (value == null) {
            digest.update((byte) 0);
            return;
        }
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update((byte) 1);
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
        digest.update(bytes);
    }

    private MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
