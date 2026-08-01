package com.vietkhampha.paymentservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vietkhampha.paymentservice.client.BookingServiceClient;
import com.vietkhampha.paymentservice.dto.CreatePaymentRequest;
import com.vietkhampha.paymentservice.dto.CreatePaymentResponse;
import com.vietkhampha.paymentservice.entity.OutboxEvent;
import com.vietkhampha.paymentservice.entity.Payment;
import com.vietkhampha.paymentservice.exception.BusinessException;
import com.vietkhampha.paymentservice.exception.ErrorCode;
import com.vietkhampha.paymentservice.repository.OutboxEventRepository;
import com.vietkhampha.paymentservice.repository.PaymentRepository;
import com.vietkhampha.paymentservice.vnpay.VNPayService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final BookingServiceClient bookingServiceClient;
    private final VNPayService vnPayService;
    private final ObjectMapper objectMapper;

    public PaymentService(PaymentRepository paymentRepository, OutboxEventRepository outboxEventRepository,
                          BookingServiceClient bookingServiceClient, VNPayService vnPayService,
                          ObjectMapper objectMapper) {
        this.paymentRepository = paymentRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.bookingServiceClient = bookingServiceClient;
        this.vnPayService = vnPayService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public CreatePaymentResponse createPayment(String userIdHeader, CreatePaymentRequest request, String ipAddress) {
        BookingServiceClient.BookingInfo bookingInfo = bookingServiceClient.getBooking(request.getBookingId(), userIdHeader);

        if (!"PENDING".equals(bookingInfo.status())) {
            throw new BusinessException(ErrorCode.BOOKING_NOT_PENDING);
        }

        Payment payment = new Payment(bookingInfo.bookingId(), Payment.Gateway.VNPAY, bookingInfo.totalAmount());
        Payment savedPayment = paymentRepository.save(payment);

        VNPayService.PaymentUrlResult result = vnPayService.createPaymentUrl(
                bookingInfo.totalAmount(),
                "Thanh toan booking " + bookingInfo.bookingId(),
                ipAddress
        );

        savedPayment.assignTransactionRef(result.transactionRef());
        paymentRepository.save(savedPayment);

        publishPaymentInitiatedEvent(savedPayment);

        return new CreatePaymentResponse(savedPayment.getId(), result.redirectUrl());
    }

    private void publishPaymentInitiatedEvent(Payment payment) {
        try {
            String payload = objectMapper.writeValueAsString(Map.of(
                    "paymentId", payment.getId().toString(),
                    "bookingId", payment.getBookingId().toString(),
                    "gatewayTransactionRef", payment.getGatewayTransactionRef(),
                    "amount", payment.getAmount()
            ));
            OutboxEvent event = new OutboxEvent("PAYMENT", payment.getId(), "payment.initiated", payload);
            outboxEventRepository.save(event);
        } catch (Exception e) {
            throw new IllegalStateException("Loi serialize outbox event", e);
        }
    }
    @Transactional
    public void confirmPayment(String txnRef) {
        Payment payment = paymentRepository.findByGatewayTransactionRef(txnRef)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));

        // Idempotent — VNPay có thể gửi IPN trùng (retry nếu server bạn phản
        // hồi chậm/lỗi mạng). Nếu đã SUCCESS, bỏ qua, không publish lại.
        if (payment.getStatus() != Payment.Status.INITIATED) {
            return;
        }

        payment.markSuccess();
        paymentRepository.save(payment);
        publishPaymentEvent(payment, "payment.succeeded");
    }

    @Transactional
    public void failPayment(String txnRef) {
        Payment payment = paymentRepository.findByGatewayTransactionRef(txnRef)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));

        if (payment.getStatus() != Payment.Status.INITIATED) {
            return;
        }

        payment.markFailed();
        paymentRepository.save(payment);
        publishPaymentEvent(payment, "payment.failed");
    }

    private void publishPaymentEvent(Payment payment, String eventType) {
        try {
            String payload = objectMapper.writeValueAsString(Map.of(
                    "paymentId", payment.getId().toString(),
                    "bookingId", payment.getBookingId().toString(),
                    "gateway", payment.getGateway().name(),
                    "amount", payment.getAmount(),
                    "gatewayTransactionRef", payment.getGatewayTransactionRef()
            ));
            OutboxEvent event = new OutboxEvent("PAYMENT", payment.getId(), eventType, payload);
            outboxEventRepository.save(event);
        } catch (Exception e) {
            throw new IllegalStateException("Loi serialize outbox event", e);
        }
    }
}