package com.vietkhampha.notificationservice.service;

import com.vietkhampha.notificationservice.dto.NotificationPageResponse;
import com.vietkhampha.notificationservice.entity.NotificationEmailDelivery;
import com.vietkhampha.notificationservice.entity.Notification;
import com.vietkhampha.notificationservice.entity.NotificationEventInbox;
import com.vietkhampha.notificationservice.entity.NotificationRecipient;
import com.vietkhampha.notificationservice.exception.BusinessException;
import com.vietkhampha.notificationservice.exception.ErrorCode;
import com.vietkhampha.notificationservice.event.UserRegisteredListener;
import com.vietkhampha.notificationservice.repository.NotificationEmailDeliveryRepository;
import com.vietkhampha.notificationservice.repository.NotificationEventInboxRepository;
import com.vietkhampha.notificationservice.repository.NotificationRepository;
import com.vietkhampha.notificationservice.repository.NotificationRecipientRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@SpringBootTest(properties = {
        "spring.kafka.listener.auto-startup=false",
        "app.notification.email.enabled=false",
        "app.notification.reminder.enabled=false",
        "app.notification.inbox.retry-delay-ms=3600000"
})
@Testcontainers
@Transactional
class NotificationFlowIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private NotificationInboxService inboxService;

    @Autowired
    private NotificationEventProcessor eventProcessor;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private DepartureReminderService reminderService;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private NotificationEmailDeliveryRepository deliveryRepository;

    @Autowired
    private NotificationEventInboxRepository inboxRepository;

    @Autowired
    private NotificationRecipientRepository recipientRepository;

    @Autowired
    private NotificationEmailDeliveryWorker emailDeliveryWorker;

    @Autowired
    private UserRegisteredListener userRegisteredListener;

    @MockBean
    private NotificationEmailSender emailSender;

    @Test
    void eventFlowIsIdempotentOwnerScopedAndCreatesReliableEmailDeliveries() {
        UUID authUserId = UUID.randomUUID();
        NotificationRecipient profile = new NotificationRecipient(authUserId);
        profile.syncIdentity("khach@example.com", "Nguyễn An");
        recipientRepository.saveAndFlush(profile);

        UUID bookingId = UUID.randomUUID();
        LocalDate departureDate = LocalDate.now().plusDays(1);
        UUID createdEventId = UUID.randomUUID();
        Map<String, Object> created = bookingEvent(
                createdEventId, "booking.created", bookingId, authUserId, departureDate,
                Map.of("holdExpiresAt", Instant.now().plusSeconds(900).toString()));

        process("booking-events", created);
        process("booking-events", created);

        assertThat(notificationRepository.count()).isEqualTo(1);
        assertThat(notificationService.unreadCount(authUserId)).isEqualTo(1);

        Map<String, Object> confirmed = bookingEvent(
                UUID.randomUUID(), "booking.confirmed", bookingId, authUserId, departureDate, Map.of());
        process("booking-events", confirmed);
        reminderService.createReminder(bookingId);
        reminderService.createReminder(bookingId);

        Map<String, Object> refund = envelope(
                UUID.randomUUID(), "refund.completed", bookingId,
                Map.of(
                        "refundId", UUID.randomUUID().toString(),
                        "bookingId", bookingId.toString(),
                        "amount", new BigDecimal("1400000"),
                        "status", "SUCCESS"
                ));
        process("payment-events", refund);

        NotificationPageResponse page = notificationService.list(authUserId, "ALL", 0, 20);
        assertThat(page.totalElements()).isEqualTo(4);
        assertThat(page.unreadCount()).isEqualTo(4);
        assertThat(page.content()).extracting(item -> item.type()).containsExactlyInAnyOrder(
                "BOOKING_HOLD_CREATED", "BOOKING_CONFIRMED", "DEPARTURE_REMINDER", "REFUND_COMPLETED");

        UUID firstNotificationId = page.content().get(0).id();
        notificationService.markRead(authUserId, firstNotificationId);
        notificationService.markRead(authUserId, firstNotificationId);
        assertThat(notificationService.unreadCount(authUserId)).isEqualTo(3);
        assertThat(notificationService.list(authUserId, "READ", 0, 20).totalElements()).isEqualTo(1);

        assertThatThrownBy(() -> notificationService.markRead(UUID.randomUUID(), firstNotificationId))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.NOTIFICATION_NOT_FOUND));

        assertThat(deliveryRepository.findAll()).hasSize(3);
        assertThat(deliveryRepository.findAll())
                .allMatch(delivery -> delivery.getStatus() == NotificationEmailDelivery.Status.SKIPPED);

        assertThat(notificationService.getPreferences(authUserId).emailEnabled()).isTrue();
        assertThat(notificationService.updatePreferences(authUserId, false).emailEnabled()).isFalse();
    }

    @Test
    void refundArrivingBeforeBookingEventIsRetainedAndRecovered() {
        UUID authUserId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        UUID refundEventId = UUID.randomUUID();
        Map<String, Object> refund = envelope(
                refundEventId, "refund.completed", bookingId,
                Map.of(
                        "refundId", UUID.randomUUID().toString(),
                        "bookingId", bookingId.toString(),
                        "amount", new BigDecimal("700000"),
                        "status", "SUCCESS"
                ));

        process("payment-events", refund);

        assertThat(inboxRepository.findById(refundEventId).orElseThrow().getStatus())
                .isEqualTo(NotificationEventInbox.Status.WAITING);
        assertThat(notificationRepository.count()).isZero();

        process("booking-events", bookingEvent(
                UUID.randomUUID(), "booking.cancelled", bookingId, authUserId,
                LocalDate.now().plusDays(10),
                Map.of("refundEligible", true, "refundPercentage", 50)));
        eventProcessor.process(refundEventId);

        assertThat(inboxRepository.findById(refundEventId).orElseThrow().getStatus())
                .isEqualTo(NotificationEventInbox.Status.PROCESSED);
        assertThat(notificationRepository.findAll())
                .extracting(Notification::getNotificationType)
                .containsExactlyInAnyOrder("BOOKING_CANCELLED", "REFUND_COMPLETED");
    }

    @Test
    void emailWorkerMarksDeliverySentAfterSuccessfulSmtpCall() {
        UUID authUserId = UUID.randomUUID();
        NotificationRecipient profile = new NotificationRecipient(authUserId);
        profile.syncIdentity("nguoinhan@example.com", "Lê Minh");
        recipientRepository.saveAndFlush(profile);
        Notification notification = notificationRepository.saveAndFlush(new Notification(
                authUserId, UUID.randomUUID(), "BOOKING_CONFIRMED", Notification.Category.PAYMENT,
                "Đặt tour đã được xác nhận", "Nội dung kiểm thử", "/bookings/test",
                "BOOKING", UUID.randomUUID().toString()
        ));
        NotificationEmailDelivery delivery = deliveryRepository.saveAndFlush(
                NotificationEmailDelivery.pending(
                        notification, profile.getEmail(), "Tiêu đề", "Nội dung", Instant.now()));

        emailDeliveryWorker.dispatch(delivery.getId());

        assertThat(deliveryRepository.findById(delivery.getId()).orElseThrow().getStatus())
                .isEqualTo(NotificationEmailDelivery.Status.SENT);
        verify(emailSender).send("nguoinhan@example.com", "Tiêu đề", "Nội dung");
    }

    @Test
    void preferenceCanBeSetBeforeIdentityEventArrives() {
        UUID authUserId = UUID.randomUUID();

        assertThat(notificationService.getPreferences(authUserId).emailEnabled()).isTrue();
        assertThat(notificationService.updatePreferences(authUserId, false).emailEnabled()).isFalse();
        assertThat(recipientRepository.findById(authUserId).orElseThrow().isEmailNotificationsEnabled())
                .isFalse();
    }

    @Test
    void identityEventUpsertsRecipientWithoutResettingPreference() {
        UUID authUserId = UUID.randomUUID();
        notificationService.updatePreferences(authUserId, false);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("userId", authUserId.toString());
        payload.put("email", "KHACH@EXAMPLE.COM");
        payload.put("fullName", "Nguyễn Minh");
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("eventType", "user.registered");
        event.put("payload", payload);

        userRegisteredListener.handle(event);
        userRegisteredListener.handle(event);

        NotificationRecipient recipient = recipientRepository.findById(authUserId).orElseThrow();
        assertThat(recipient.getEmail()).isEqualTo("khach@example.com");
        assertThat(recipient.getFullName()).isEqualTo("Nguyễn Minh");
        assertThat(recipient.isEmailNotificationsEnabled()).isFalse();
    }

    @Test
    void emailWorkerKeepsFailedDeliveryForBackoffRetry() {
        UUID authUserId = UUID.randomUUID();
        NotificationRecipient profile = new NotificationRecipient(authUserId);
        profile.syncIdentity("retry@example.com", "Trần An");
        recipientRepository.saveAndFlush(profile);
        Notification notification = notificationRepository.saveAndFlush(new Notification(
                authUserId, UUID.randomUUID(), "BOOKING_CONFIRMED", Notification.Category.PAYMENT,
                "Đặt tour đã được xác nhận", "Nội dung kiểm thử retry", "/bookings/test",
                "BOOKING", UUID.randomUUID().toString()
        ));
        NotificationEmailDelivery delivery = deliveryRepository.saveAndFlush(
                NotificationEmailDelivery.pending(
                        notification, profile.getEmail(), "Tiêu đề retry", "Nội dung retry", Instant.now()));
        doThrow(new IllegalStateException("SMTP tạm thời không phản hồi"))
                .when(emailSender).send("retry@example.com", "Tiêu đề retry", "Nội dung retry");

        emailDeliveryWorker.dispatch(delivery.getId());

        NotificationEmailDelivery persisted = deliveryRepository.findById(delivery.getId()).orElseThrow();
        assertThat(persisted.getStatus()).isEqualTo(NotificationEmailDelivery.Status.PENDING);
        assertThat(persisted.getAttemptCount()).isEqualTo(1);
        assertThat(persisted.getNextAttemptAt()).isAfter(Instant.now());
        assertThat(persisted.getLastError()).contains("SMTP tạm thời không phản hồi");
    }

    private void process(String topic, Map<String, Object> event) {
        UUID eventId = inboxService.accept(topic, event);
        eventProcessor.process(eventId);
    }

    private Map<String, Object> bookingEvent(
            UUID eventId, String eventType, UUID bookingId, UUID authUserId,
            LocalDate departureDate, Map<String, Object> extraPayload) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("bookingId", bookingId.toString());
        payload.put("customerId", authUserId.toString());
        payload.put("tourId", "tour-da-lat");
        payload.put("startDate", departureDate.toString());
        payload.put("endDate", departureDate.plusDays(2).toString());
        payload.put("totalAmount", new BigDecimal("2800000"));
        payload.putAll(extraPayload);
        return envelope(eventId, eventType, bookingId, payload);
    }

    private Map<String, Object> envelope(
            UUID eventId, String eventType, UUID aggregateId, Map<String, Object> payload) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("eventId", eventId.toString());
        envelope.put("eventType", eventType);
        envelope.put("aggregateId", aggregateId.toString());
        envelope.put("occurredAt", Instant.now().toString());
        envelope.put("payload", payload);
        return envelope;
    }
}
