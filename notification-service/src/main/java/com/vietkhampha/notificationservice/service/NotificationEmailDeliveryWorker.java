package com.vietkhampha.notificationservice.service;

import com.vietkhampha.notificationservice.entity.NotificationEmailDelivery;
import com.vietkhampha.notificationservice.entity.NotificationRecipient;
import com.vietkhampha.notificationservice.repository.NotificationEmailDeliveryRepository;
import com.vietkhampha.notificationservice.repository.NotificationRecipientRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class NotificationEmailDeliveryWorker {

    private static final Logger log = LoggerFactory.getLogger(NotificationEmailDeliveryWorker.class);

    private final NotificationEmailDeliveryRepository deliveryRepository;
    private final NotificationRecipientRepository recipientRepository;
    private final NotificationEmailSender emailSender;
    private final int maxAttempts;

    public NotificationEmailDeliveryWorker(
            NotificationEmailDeliveryRepository deliveryRepository,
            NotificationRecipientRepository recipientRepository,
            NotificationEmailSender emailSender,
            @Value("${app.notification.email.max-attempts:5}") int maxAttempts
    ) {
        this.deliveryRepository = deliveryRepository;
        this.recipientRepository = recipientRepository;
        this.emailSender = emailSender;
        this.maxAttempts = maxAttempts;
    }

    @Transactional
    public void dispatch(UUID deliveryId) {
        NotificationEmailDelivery delivery = deliveryRepository.findByIdForUpdate(deliveryId).orElse(null);
        if (delivery == null || !isDispatchable(delivery)) return;

        Instant now = Instant.now();
        Optional<NotificationRecipient> profile = recipientRepository.findById(
                delivery.getNotification().getAuthUserId());
        if (profile.isPresent() && !profile.get().isEmailNotificationsEnabled()) {
            delivery.markSkipped("Người dùng đã tắt thông báo email", now);
            return;
        }

        if (delivery.getStatus() == NotificationEmailDelivery.Status.WAITING_RECIPIENT) {
            String email = profile.map(NotificationRecipient::getEmail).orElse(null);
            if (email == null || email.isBlank()) {
                delivery.recordFailure("Chưa có địa chỉ email của người nhận", now, maxAttempts);
                return;
            }
            delivery.resolveRecipient(email, now);
        }

        try {
            emailSender.send(delivery.getRecipientEmail(), delivery.getSubject(), delivery.getBody());
            delivery.markSent(now);
            log.info("Đã gửi email cho notification {}", delivery.getNotification().getId());
        } catch (Exception exception) {
            String reason = exception.getClass().getSimpleName()
                    + (exception.getMessage() == null ? "" : ": " + exception.getMessage());
            delivery.recordFailure(reason, now, maxAttempts);
            log.warn("Gửi email notification {} chưa thành công, lần thử {}",
                    delivery.getNotification().getId(), delivery.getAttemptCount());
        }
    }

    private boolean isDispatchable(NotificationEmailDelivery delivery) {
        return delivery.getStatus() == NotificationEmailDelivery.Status.PENDING
                || delivery.getStatus() == NotificationEmailDelivery.Status.WAITING_RECIPIENT;
    }
}
