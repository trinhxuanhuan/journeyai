package com.vietkhampha.paymentservice.controller;

import com.vietkhampha.paymentservice.dto.CreatePaymentRequest;
import com.vietkhampha.paymentservice.dto.CreatePaymentResponse;
import com.vietkhampha.paymentservice.service.PaymentService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
            @Valid @RequestBody CreatePaymentRequest request,
            HttpServletRequest httpRequest
    ) {

        String ipAddress = httpRequest.getRemoteAddr();
        CreatePaymentResponse response = paymentService.createPayment(userIdHeader, request, ipAddress);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

}
