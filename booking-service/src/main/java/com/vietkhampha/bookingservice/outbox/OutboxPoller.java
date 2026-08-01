package com.vietkhampha.bookingservice.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vietkhampha.bookingservice.entity.OutboxEvent;
import com.vietkhampha.bookingservice.repository.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class OutboxPoller {

    private static final Logger log = LoggerFactory.getLogger(OutboxPoller.class);
    private static final String TOPIC = "booking-events";

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
                Map<String, Object> message = new LinkedHashMap<>();
                message.put("eventType", event.getEventType());
                message.put("aggregateId", event.getAggregateId().toString());
                message.put("occurredAt", event.getCreatedAt().toString());
                message.put("payload", objectMapper.readValue(event.getPayload(), Map.class));

                kafkaTemplate.send(TOPIC, event.getAggregateId().toString(), message).get();
                event.markPublished();
                outboxEventRepository.save(event);
                log.info("Da publish outbox event {} (type={})", event.getId(), event.getEventType());
            } catch (Exception e) {
                log.error("Loi khi publish outbox event {}, se thu lai o lan quet sau", event.getId(), e);
            }
        }
    }
}