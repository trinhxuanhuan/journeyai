package com.vietkhampha.bookingservice.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vietkhampha.bookingservice.service.BookingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Component
public class PaymentEventListener {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventListener.class);

    private final BookingService bookingService;
    private final ObjectMapper objectMapper;

    public PaymentEventListener(BookingService bookingService, ObjectMapper objectMapper) {
        this.bookingService = bookingService;
        this.objectMapper = objectMapper;
    }

    @SuppressWarnings("unchecked")
    @KafkaListener(topics = "payment-events", groupId = "booking-service-payment-consumer")
    public void handlePaymentEvent(Map<String, Object> event) {
        String eventType = (String) event.get("eventType");

        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) event.get("payload");
        UUID bookingId = UUID.fromString((String) payload.get("bookingId"));

        switch (eventType) {
            case "payment.succeeded" -> {
                bookingService.confirmBookingPayment(bookingId);
                log.info("Da xac nhan thanh toan cho booking {}", bookingId);
            }
            case "payment.failed" -> {
                bookingService.failBookingPayment(bookingId, "Thanh toan that bai");
                log.info("Da xu ly thanh toan that bai cho booking {}", bookingId);
            }
            default -> log.warn("Nhan eventType khong xac dinh: {}", eventType);
        }
    }
}