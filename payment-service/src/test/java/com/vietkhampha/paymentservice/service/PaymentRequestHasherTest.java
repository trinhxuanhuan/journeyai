package com.vietkhampha.paymentservice.service;

import com.vietkhampha.paymentservice.dto.CreatePaymentRequest;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentRequestHasherTest {

    private static final UUID CUSTOMER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID BOOKING_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private final PaymentRequestHasher hasher = new PaymentRequestHasher();

    @Test
    void fixedContractVector_matchesSha256V1GoldenHash() {
        String hash = hasher.hash(CUSTOMER_ID, request(BOOKING_ID, "VNPAY"));

        assertThat(hash).isEqualTo("e63dcef6b665b1ef93fddcc904925720d77d4962d68597fdf6229c5ed34224fe");
    }

    @Test
    void sameRequest_isDeterministicLowercaseSha256() {
        CreatePaymentRequest request = request(BOOKING_ID, "VNPAY");

        String first = hasher.hash(CUSTOMER_ID, request);
        String second = hasher.hash(CUSTOMER_ID, request);

        assertThat(first).isEqualTo(second).matches("^[0-9a-f]{64}$");
    }

    @Test
    void gatewayCase_isCanonicalized() {
        String uppercase = hasher.hash(CUSTOMER_ID, request(BOOKING_ID, "VNPAY"));
        String lowercase = hasher.hash(CUSTOMER_ID, request(BOOKING_ID, "vnpay"));

        assertThat(lowercase).isEqualTo(uppercase);
    }

    @Test
    void changingCustomerBookingOrGateway_changesHash() {
        String baseline = hasher.hash(CUSTOMER_ID, request(BOOKING_ID, "VNPAY"));

        assertThat(hasher.hash(UUID.randomUUID(), request(BOOKING_ID, "VNPAY"))).isNotEqualTo(baseline);
        assertThat(hasher.hash(CUSTOMER_ID, request(UUID.randomUUID(), "VNPAY"))).isNotEqualTo(baseline);
        assertThat(hasher.hash(CUSTOMER_ID, request(BOOKING_ID, "STRIPE"))).isNotEqualTo(baseline);
    }

    private CreatePaymentRequest request(UUID bookingId, String gateway) {
        TestCreatePaymentRequest request = new TestCreatePaymentRequest();
        request.setBookingId(bookingId);
        request.setGateway(gateway);
        return request;
    }

    private static final class TestCreatePaymentRequest extends CreatePaymentRequest {
        private TestCreatePaymentRequest() {
        }
    }
}
