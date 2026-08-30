package com.vietkhampha.paymentservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vietkhampha.paymentservice.entity.Payment;
import com.vietkhampha.paymentservice.repository.PaymentLogRepository;
import com.vietkhampha.paymentservice.repository.PaymentRepository;
import com.vietkhampha.paymentservice.service.PaymentService;
import com.vietkhampha.paymentservice.vnpay.VNPayService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.net.URI;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VNPayReturnControllerTest {

    private final VNPayService vnPayService = mock(VNPayService.class);
    private final PaymentService paymentService = mock(PaymentService.class);
    private final PaymentRepository paymentRepository = mock(PaymentRepository.class);
    private final PaymentLogRepository paymentLogRepository = mock(PaymentLogRepository.class);
    private final VNPayWebhookController controller = new VNPayWebhookController(
            vnPayService,
            paymentService,
            paymentRepository,
            paymentLogRepository,
            new ObjectMapper(),
            "https://vietkhampha.test/thanh-toan/ket-qua"
    );

    @Test
    void redirectsSuccessfulReturnWithStableResourceIdentifiers() throws Exception {
        UUID paymentId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        Payment payment = mock(Payment.class);
        when(payment.getId()).thenReturn(paymentId);
        when(payment.getBookingId()).thenReturn(bookingId);
        when(paymentRepository.findByGatewayTransactionRef("TXN-1")).thenReturn(Optional.of(payment));
        when(vnPayService.verifyChecksum(Map.of(
                "vnp_TxnRef", "TXN-1",
                "vnp_ResponseCode", "00"
        ))).thenReturn(true);

        ResponseEntity<Void> response = controller.handleReturn(Map.of(
                "vnp_TxnRef", "TXN-1",
                "vnp_ResponseCode", "00"
        ));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        assertThat(response.getHeaders().getLocation()).isEqualTo(URI.create(
                "https://vietkhampha.test/thanh-toan/ket-qua"
                        + "?gatewayResult=success&paymentId=" + paymentId
                        + "&bookingId=" + bookingId
        ));
    }

    @Test
    void redirectsInvalidReturnWithoutExposingPaymentIdentity() throws Exception {
        Map<String, String> params = Map.of(
                "vnp_TxnRef", "KNOWN-BUT-UNTRUSTED",
                "vnp_ResponseCode", "00"
        );
        when(vnPayService.verifyChecksum(params)).thenReturn(false);

        ResponseEntity<Void> response = controller.handleReturn(params);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        assertThat(response.getHeaders().getLocation()).isEqualTo(URI.create(
                "https://vietkhampha.test/thanh-toan/ket-qua?gatewayResult=invalid"
        ));
        verify(paymentRepository, times(1))
                .findByGatewayTransactionRef("KNOWN-BUT-UNTRUSTED");
    }
}
