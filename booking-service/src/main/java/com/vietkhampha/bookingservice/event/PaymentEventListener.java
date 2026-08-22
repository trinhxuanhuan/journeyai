package com.vietkhampha.bookingservice.event;

import com.vietkhampha.bookingservice.repository.ProcessedPaymentEventRepository;
import com.vietkhampha.bookingservice.service.BookingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Component
public class PaymentEventListener {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventListener.class);
    private static final Set<String> SUPPORTED_EVENT_TYPES = Set.of(
            "payment.succeeded",
            "payment.failed"
    );

    private final BookingService bookingService;
    private final ProcessedPaymentEventRepository processedPaymentEventRepository;

    public PaymentEventListener(
            BookingService bookingService,
            ProcessedPaymentEventRepository processedPaymentEventRepository
    ) {
        this.bookingService = bookingService;
        this.processedPaymentEventRepository = processedPaymentEventRepository;
    }

    @Transactional
    @KafkaListener(topics = "payment-events", groupId = "booking-service-payment-consumer")
    public void handlePaymentEvent(Map<String, Object> event) {
        String eventType = requiredString(event, "eventType");
        if (!SUPPORTED_EVENT_TYPES.contains(eventType)) {
            log.debug("Bo qua eventType khong thuoc Booking Service: {}", eventType);
            return;
        }

        PaymentEvent paymentEvent = parsePaymentEvent(event, eventType);
        boolean claimed = processedPaymentEventRepository.tryClaim(
                paymentEvent.eventId(),
                paymentEvent.paymentId(),
                paymentEvent.bookingId(),
                paymentEvent.eventType()
        );
        if (!claimed) {
            if (!processedPaymentEventRepository.matchesExistingClaim(
                    paymentEvent.eventId(),
                    paymentEvent.paymentId(),
                    paymentEvent.bookingId(),
                    paymentEvent.eventType()
            )) {
                throw new IllegalStateException("Payment event identity collides with a different event");
            }
            log.info("Bo qua payment event da xu ly {}", paymentEvent.eventId());
            return;
        }

        switch (eventType) {
            case "payment.succeeded" -> bookingService.confirmBookingPayment(paymentEvent.bookingId());
            case "payment.failed" ->
                    bookingService.failBookingPayment(paymentEvent.bookingId(), "Thanh toan that bai");
            default -> throw new IllegalStateException("Unsupported payment event reached processing");
        }
    }

    private PaymentEvent parsePaymentEvent(Map<String, Object> event, String eventType) {
        Object rawPayload = event.get("payload");
        if (!(rawPayload instanceof Map<?, ?> payload)) {
            throw new IllegalArgumentException("Payment event payload must be an object");
        }

        UUID paymentId = requiredUuid(payload, "paymentId");
        UUID bookingId = requiredUuid(payload, "bookingId");
        Object rawAggregateId = event.get("aggregateId");
        if (rawAggregateId != null && !paymentId.equals(parseUuid(rawAggregateId, "aggregateId"))) {
            throw new IllegalArgumentException("Payment event aggregateId does not match payload.paymentId");
        }

        UUID eventId = event.get("eventId") == null
                ? legacyEventId(eventType, paymentId)
                : parseUuid(event.get("eventId"), "eventId");
        return new PaymentEvent(eventId, paymentId, bookingId, eventType);
    }

    private String requiredString(Map<String, Object> source, String fieldName) {
        Object rawValue = source.get(fieldName);
        if (!(rawValue instanceof String value) || value.isBlank()) {
            throw new IllegalArgumentException("Payment event is missing " + fieldName);
        }
        return value;
    }

    private UUID requiredUuid(Map<?, ?> source, String fieldName) {
        Object rawValue = source.get(fieldName);
        if (rawValue == null) {
            throw new IllegalArgumentException("Payment event payload is missing " + fieldName);
        }
        return parseUuid(rawValue, "payload." + fieldName);
    }

    private UUID parseUuid(Object rawValue, String fieldName) {
        if (!(rawValue instanceof String value) || value.isBlank()) {
            throw new IllegalArgumentException("Payment event " + fieldName + " must be a UUID string");
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Payment event " + fieldName + " must be a UUID string", exception);
        }
    }

    private UUID legacyEventId(String eventType, UUID paymentId) {
        String identity = "payment-event-v1|" + eventType + "|" + paymentId;
        return UUID.nameUUIDFromBytes(identity.getBytes(StandardCharsets.UTF_8));
    }

    private record PaymentEvent(UUID eventId, UUID paymentId, UUID bookingId, String eventType) {
    }
}
