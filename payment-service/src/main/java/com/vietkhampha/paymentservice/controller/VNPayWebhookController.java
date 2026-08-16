package com.vietkhampha.paymentservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vietkhampha.paymentservice.dto.VNPayIpnResponse;
import com.vietkhampha.paymentservice.entity.PaymentLog;
import com.vietkhampha.paymentservice.repository.PaymentLogRepository;
import com.vietkhampha.paymentservice.repository.PaymentRepository;
import com.vietkhampha.paymentservice.service.PaymentService;
import com.vietkhampha.paymentservice.vnpay.VNPayService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class VNPayWebhookController {

    private static final Logger log = LoggerFactory.getLogger(VNPayWebhookController.class);

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
    public VNPayIpnResponse handleIpn(@RequestParam Map<String, String> allParams) {
        if (!vnPayService.verifyChecksum(allParams)) {
            return VNPayIpnResponse.invalidChecksum();
        }

        try {
            return paymentService.processVnPayIpn(allParams);
        } catch (RuntimeException e) {
            log.error("Khong the xu ly VNPay IPN cho txnRef={}", allParams.get("vnp_TxnRef"), e);
            return VNPayIpnResponse.invalidRequest();
        }
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
