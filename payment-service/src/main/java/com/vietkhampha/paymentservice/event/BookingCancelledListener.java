package com.vietkhampha.paymentservice.event;

import com.vietkhampha.paymentservice.service.PaymentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
@Component
public class BookingCancelledListener {

    private static final Logger log = LoggerFactory.getLogger(BookingCancelledListener.class);

    private final PaymentService paymentService;

    public BookingCancelledListener(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @SuppressWarnings("unchecked")
    @KafkaListener(topics = "booking-events", groupId = "payment-service-refund-consumer")
    public void handleBookingEvent(Map<String, Object> event) {
        String eventType = (String) event.get("eventType");
        if (!"booking.cancelled".equals(eventType)) {
            return; // Bỏ qua booking.created/confirmed/expired — chỉ quan tâm cancelled
        }

        Map<String, Object> payload = (Map<String, Object>) event.get("payload");
        boolean refundEligible = Boolean.TRUE.equals(payload.get("refundEligible"));

        if (!refundEligible) {
            log.info("Booking {} huy nhung khong du dieu kien hoan tien (0%)", payload.get("bookingId"));
            return;
        }

        UUID bookingId = UUID.fromString((String) payload.get("bookingId"));
        int refundPercentage = ((Number) payload.get("refundPercentage")).intValue();
        
        paymentService.processRefund(bookingId, refundPercentage, "127.0.0.1");
        log.info("Da xu ly yeu cau hoan tien cho booking {}", bookingId);
    }
}