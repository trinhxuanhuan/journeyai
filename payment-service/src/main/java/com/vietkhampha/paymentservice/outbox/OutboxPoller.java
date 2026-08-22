package com.vietkhampha.paymentservice.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vietkhampha.paymentservice.entity.OutboxEvent;
import com.vietkhampha.paymentservice.repository.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@ConditionalOnProperty(name = "app.outbox.poller.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxPoller {

    private static final Logger log = LoggerFactory.getLogger(OutboxPoller.class);
    private static final String TOPIC = "payment-events";

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public OutboxPoller(OutboxEventRepository outboxEventRepository, KafkaTemplate<String, Object> kafkaTemplate,
                        ObjectMapper objectMapper) {
        this.outboxEventRepository = outboxEventRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedDelay = 2000)
    @Transactional
    public void pollAndPublish() {
        List<OutboxEvent> pending = outboxEventRepository.findByPublishedFalseOrderByCreatedAtAsc();

        for (OutboxEvent event : pending) {
            try {
                Map<String, Object> payload = objectMapper.readValue(event.getPayload(), Map.class);
                UUID bookingId = requiredUuid(payload, "bookingId");

                Map<String, Object> message = new LinkedHashMap<>();
                message.put("eventId", event.getId().toString());
                message.put("eventType", event.getEventType());
                message.put("aggregateId", event.getAggregateId().toString());
                message.put("occurredAt", event.getCreatedAt().toString());
                message.put("payload", payload);

                kafkaTemplate.send(TOPIC, bookingId.toString(), message).get();
                event.markPublished();
                outboxEventRepository.save(event);
                log.info("Da publish outbox event {} (type={})", event.getId(), event.getEventType());
            } catch (Exception e) {
                log.error("Loi khi publish outbox event {}, se thu lai o lan quet sau", event.getId(), e);
            }
        }
    }

    private UUID requiredUuid(Map<String, Object> payload, String fieldName) {
        Object rawValue = payload.get(fieldName);
        if (!(rawValue instanceof String value) || value.isBlank()) {
            throw new IllegalArgumentException("Payment event payload is missing " + fieldName);
        }
        return UUID.fromString(value);
    }
}
