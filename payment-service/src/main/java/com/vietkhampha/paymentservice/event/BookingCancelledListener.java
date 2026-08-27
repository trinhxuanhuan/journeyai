package com.vietkhampha.paymentservice.event;

import com.vietkhampha.paymentservice.service.PaymentService;
import com.vietkhampha.paymentservice.repository.ProcessedBookingEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Component
public class BookingCancelledListener {

    private static final Logger log = LoggerFactory.getLogger(BookingCancelledListener.class);

    private final PaymentService paymentService;
    private final ProcessedBookingEventRepository processedBookingEventRepository;

    public BookingCancelledListener(PaymentService paymentService,
                                    ProcessedBookingEventRepository processedBookingEventRepository) {
        this.paymentService = paymentService;
        this.processedBookingEventRepository = processedBookingEventRepository;
    }

    @SuppressWarnings("unchecked")
    @Transactional
    @KafkaListener(topics = "booking-events", groupId = "payment-service-refund-consumer")
    public void handleBookingEvent(Map<String, Object> event) {
        String eventType = (String) event.get("eventType");

        if (!"booking.cancelled".equals(eventType)
                && !"booking.late_payment_refund_required".equals(eventType)) {
            return;
        }
        Map<String, Object> payload = (Map<String, Object>) event.get("payload");
        UUID bookingId = UUID.fromString((String) payload.get("bookingId"));
        UUID eventId = event.get("eventId") == null
                ? UUID.nameUUIDFromBytes((eventType + "|" + bookingId).getBytes(java.nio.charset.StandardCharsets.UTF_8))
                : UUID.fromString(event.get("eventId").toString());
        if (!processedBookingEventRepository.tryClaim(eventId, bookingId, eventType)) {
            log.info("Bo qua booking event da xu ly {}", eventId);
            return;
        }

        switch (eventType) {
            case "booking.cancelled" -> handleBookingCancelled(event);
            case "booking.late_payment_refund_required" -> handleLatePaymentRefundRequired(event);
            default -> { }
        }
    }

    @SuppressWarnings("unchecked")
    private void handleBookingCancelled(Map<String, Object> event) {
        Map<String, Object> payload = (Map<String, Object>) event.get("payload");
        boolean refundEligible = Boolean.TRUE.equals(payload.get("refundEligible"));

        if (!refundEligible) {
            log.info("Booking {} huy nhung khong du dieu kien hoan tien (0%)", payload.get("bookingId"));
            return;
        }

        UUID bookingId = UUID.fromString((String) payload.get("bookingId"));
        int refundPercentage = ((Number) payload.get("refundPercentage")).intValue();

        paymentService.processRefund(bookingId, refundPercentage, "127.0.0.1");
        log.info("Da xu ly yeu cau hoan tien cho booking {} (huy boi khach hang, {}%)", bookingId, refundPercentage);
    }

    @SuppressWarnings("unchecked")
    private void handleLatePaymentRefundRequired(Map<String, Object> event) {
        Map<String, Object> payload = (Map<String, Object>) event.get("payload");

        UUID bookingId = UUID.fromString((String) payload.get("bookingId"));
        int refundPercentage = ((Number) payload.get("refundPercentage")).intValue();

        paymentService.processRefund(bookingId, refundPercentage, "127.0.0.1");
        log.info("Da xu ly hoan tien tu dong cho booking {} (thanh toan den muon, het cho, {}%)", bookingId, refundPercentage);
    }
}
