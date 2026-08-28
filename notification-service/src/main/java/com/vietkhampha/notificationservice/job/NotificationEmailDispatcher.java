package com.vietkhampha.notificationservice.job;

import com.vietkhampha.notificationservice.repository.NotificationEmailDeliveryRepository;
import com.vietkhampha.notificationservice.service.NotificationEmailDeliveryWorker;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
@ConditionalOnProperty(
        prefix = "app.notification.email",
        name = "enabled",
        havingValue = "true"
)
public class NotificationEmailDispatcher {

    private final NotificationEmailDeliveryRepository deliveryRepository;
    private final NotificationEmailDeliveryWorker worker;

    public NotificationEmailDispatcher(NotificationEmailDeliveryRepository deliveryRepository,
                                       NotificationEmailDeliveryWorker worker) {
        this.deliveryRepository = deliveryRepository;
        this.worker = worker;
    }

    @Scheduled(fixedDelayString = "${app.notification.email.dispatch-delay-ms:5000}")
    public void dispatch() {
        List<UUID> deliveryIds = deliveryRepository.findDispatchableIds(
                Instant.now(), PageRequest.of(0, 50));
        deliveryIds.forEach(worker::dispatch);
    }
}
