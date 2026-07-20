package com.vietkhampha.bookingservice.outbox;

import com.vietkhampha.bookingservice.entity.OutboxEvent;
import com.vietkhampha.bookingservice.repository.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
public class OutboxPoller {

    private static final Logger log = LoggerFactory.getLogger(OutboxPoller.class);
    private static final String TOPIC = "booking-events";

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public OutboxPoller(OutboxEventRepository outboxEventRepository, KafkaTemplate<String, Object> kafkaTemplate) {
        this.outboxEventRepository = outboxEventRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Scheduled(fixedDelay = 2000)
    @Transactional
    public void pollAndPublish() {
        List<OutboxEvent> pending = outboxEventRepository.findByPublishedFalseOrderByCreatedAtAsc();

        for (OutboxEvent event : pending) {
            try {

                kafkaTemplate.send(TOPIC, event.getAggregateId().toString(), event).get();
                event.markPublished();
                outboxEventRepository.save(event);
                log.info("Da publish outbox event {} (type={})", event.getId(), event.getEventType());
            } catch (Exception e) {
                log.error("Loi khi publish outbox event {}, se thu lai o lan quet sau", event.getId(), e);

            }
        }
    }
}