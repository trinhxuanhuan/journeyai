package com.vietkhampha.notificationservice.job;

import com.vietkhampha.notificationservice.repository.NotificationEventInboxRepository;
import com.vietkhampha.notificationservice.service.NotificationEventProcessor;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
public class NotificationInboxRetryJob {

    private final NotificationEventInboxRepository inboxRepository;
    private final NotificationEventProcessor eventProcessor;

    public NotificationInboxRetryJob(NotificationEventInboxRepository inboxRepository,
                                     NotificationEventProcessor eventProcessor) {
        this.inboxRepository = inboxRepository;
        this.eventProcessor = eventProcessor;
    }

    @Scheduled(fixedDelayString = "${app.notification.inbox.retry-delay-ms:10000}")
    public void retryPendingEvents() {
        List<UUID> eventIds = inboxRepository.findRetryableEventIds(Instant.now(), PageRequest.of(0, 50));
        eventIds.forEach(eventProcessor::process);
    }
}
