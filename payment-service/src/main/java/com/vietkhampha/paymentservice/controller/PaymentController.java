package com.vietkhampha.paymentservice.controller;

import com.vietkhampha.paymentservice.dto.CreatePaymentRequest;
import com.vietkhampha.paymentservice.dto.CreatePaymentResponse;
import com.vietkhampha.paymentservice.dto.PaymentStatusResponse;
import com.vietkhampha.paymentservice.service.PaymentService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/v1/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping
    public ResponseEntity<CreatePaymentResponse> createPayment(
            @RequestHeader("X-User-Id") String userIdHeader,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CreatePaymentRequest request,
            HttpServletRequest httpRequest
    ) {

        String ipAddress = httpRequest.getRemoteAddr();
        PaymentService.PaymentResult result = paymentService.createPayment(
                userIdHeader,
                idempotencyKey,
                request,
                ipAddress
        );
        HttpStatus status = result.replay() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(result.response());
    }

    @GetMapping("/{paymentId}")
    public ResponseEntity<PaymentStatusResponse> getPayment(
            @RequestHeader("X-User-Id") String userIdHeader,
            @PathVariable UUID paymentId
    ) {
        return ResponseEntity.ok(paymentService.getPayment(paymentId, userIdHeader));
    }

}
