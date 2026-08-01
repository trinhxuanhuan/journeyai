package com.vietkhampha.paymentservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vietkhampha.paymentservice.entity.PaymentLog;
import com.vietkhampha.paymentservice.repository.PaymentLogRepository;
import com.vietkhampha.paymentservice.repository.PaymentRepository;
import com.vietkhampha.paymentservice.service.PaymentService;
import com.vietkhampha.paymentservice.vnpay.VNPayService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class VNPayWebhookController {

    private final VNPayService vnPayService;
    private final PaymentService paymentService;
    private final PaymentRepository paymentRepository;
    private final PaymentLogRepository paymentLogRepository;
    private final ObjectMapper objectMapper;

    public VNPayWebhookController(VNPayService vnPayService, PaymentService paymentService,
                                  PaymentRepository paymentRepository, PaymentLogRepository paymentLogRepository,
                                  ObjectMapper objectMapper) {
        this.vnPayService = vnPayService;
        this.paymentService = paymentService;
        this.paymentRepository = paymentRepository;
        this.paymentLogRepository = paymentLogRepository;
        this.objectMapper = objectMapper;
    }
    @GetMapping("/v1/payments/webhooks/vnpay")
    public Map<String, String> handleIpn(@RequestParam Map<String, String> allParams) throws Exception {
        logRawPayload(allParams, "WEBHOOK_IPN");

        if (!vnPayService.verifyChecksum(allParams)) {
            return Map.of("RspCode", "97", "Message", "Invalid Checksum");
        }

        String txnRef = allParams.get("vnp_TxnRef");
        String responseCode = allParams.get("vnp_ResponseCode");

        if (paymentRepository.findByGatewayTransactionRef(txnRef).isEmpty()) {
            return Map.of("RspCode", "01", "Message", "Order not found");
        }

        if ("00".equals(responseCode)) {
            paymentService.confirmPayment(txnRef);
        } else {
            paymentService.failPayment(txnRef);
        }

        return Map.of("RspCode", "00", "Message", "Confirm Success");
    }
    @GetMapping("/v1/payments/vnpay-return")
    public Map<String, Object> handleReturn(@RequestParam Map<String, String> allParams) throws Exception {
        logRawPayload(allParams, "RETURN_REDIRECT");

        boolean validChecksum = vnPayService.verifyChecksum(allParams);
        boolean success = validChecksum && "00".equals(allParams.get("vnp_ResponseCode"));

        return Map.of(
                "success", success,
                "validChecksum", validChecksum,
                "message", success ? "Thanh toan thanh cong, vui long doi xac nhan" : "Thanh toan khong thanh cong"
        );
    }

    private void logRawPayload(Map<String, String> allParams, String source) throws Exception {
        String txnRef = allParams.get("vnp_TxnRef");
        paymentRepository.findByGatewayTransactionRef(txnRef).ifPresent(payment -> {
            try {
                String raw = objectMapper.writeValueAsString(allParams);
                paymentLogRepository.save(new PaymentLog(payment.getId(), source, raw));
            } catch (Exception ignored) {
            }
        });
    }

}
