package com.vietkhampha.notificationservice.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vietkhampha.notificationservice.entity.BookingNotificationRecipient;
import com.vietkhampha.notificationservice.entity.Notification;
import com.vietkhampha.notificationservice.entity.NotificationEventInbox;
import com.vietkhampha.notificationservice.repository.BookingNotificationRecipientRepository;
import com.vietkhampha.notificationservice.repository.NotificationEventInboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class NotificationEventProcessor {

    private static final Logger log = LoggerFactory.getLogger(NotificationEventProcessor.class);
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm 'ngày' dd/MM/yyyy");
    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final NotificationEventInboxRepository inboxRepository;
    private final BookingNotificationRecipientRepository recipientRepository;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;
    private final int maxAttempts;

    public NotificationEventProcessor(
            NotificationEventInboxRepository inboxRepository,
            BookingNotificationRecipientRepository recipientRepository,
            NotificationService notificationService,
            ObjectMapper objectMapper,
            @Value("${app.notification.inbox.max-attempts:10}") int maxAttempts
    ) {
        this.inboxRepository = inboxRepository;
        this.recipientRepository = recipientRepository;
        this.notificationService = notificationService;
        this.objectMapper = objectMapper;
        this.maxAttempts = maxAttempts;
    }

    @Transactional
    public void process(UUID eventId) {
        NotificationEventInbox inbox = inboxRepository.findByIdForUpdate(eventId).orElse(null);
        if (inbox == null || inbox.isTerminal()) return;

        Instant now = Instant.now();
        try {
            Map<String, Object> envelope = objectMapper.readValue(
                    inbox.getEventPayload(), new TypeReference<>() { });
            Map<String, Object> payload = payload(envelope);
            boolean handled = switch (inbox.getSourceTopic()) {
                case "booking-events" -> processBookingEvent(inbox, payload);
                case "payment-events" -> processPaymentEvent(inbox, payload);
                default -> false;
            };
            if (handled) inbox.markProcessed(now);
            else inbox.markIgnored(now, "Event không tạo thông báo cho khách hàng");
        } catch (PermanentEventException exception) {
            inbox.markFailed(now, exception.getMessage());
            log.warn("Từ chối notification event {}: {}", eventId, exception.getMessage());
        } catch (DeferredEventException exception) {
            deferOrFail(inbox, now, exception.getMessage());
        } catch (Exception exception) {
            deferOrFail(inbox, now, exception.getClass().getSimpleName() + ": " + exception.getMessage());
            log.error("Lỗi xử lý notification event {}, sẽ thử lại nếu còn lượt", eventId, exception);
        }
        inboxRepository.save(inbox);
    }

    private boolean processBookingEvent(NotificationEventInbox inbox, Map<String, Object> payload) {
        String eventType = inbox.getEventType();
        if (!isCustomerFacingBookingEvent(eventType)) return false;

        UUID bookingId = requiredUuid(payload, "bookingId");
        UUID authUserId = requiredUuid(payload, "customerId");
        BookingNotificationRecipient recipient = recipientRepository.findByIdForUpdate(bookingId)
                .orElseGet(() -> new BookingNotificationRecipient(bookingId, authUserId));
        recipient.applySnapshot(
                authUserId,
                text(payload, "tourId"),
                bookingStatus(eventType),
                localDate(payload, "startDate"),
                localDate(payload, "endDate")
        );
        recipientRepository.save(recipient);

        NotificationService.NotificationDraft draft = bookingDraft(
                inbox.getEventId(), eventType, bookingId, authUserId, payload);
        notificationService.create(draft);
        return true;
    }

    private boolean processPaymentEvent(NotificationEventInbox inbox, Map<String, Object> payload) {
        String eventType = inbox.getEventType();
        if (!"refund.completed".equals(eventType) && !"refund.manual_required".equals(eventType)) {
            return false;
        }

        UUID bookingId = requiredUuid(payload, "bookingId");
        BookingNotificationRecipient recipient = recipientRepository.findById(bookingId)
                .orElseThrow(() -> new DeferredEventException(
                        "Chưa xác định được người nhận của booking " + bookingId));
        String amount = formatAmount(payload.get("amount"));
        NotificationService.NotificationDraft draft;
        if ("refund.completed".equals(eventType)) {
            draft = new NotificationService.NotificationDraft(
                    inbox.getEventId(), recipient.getAuthUserId(), "REFUND_COMPLETED",
                    Notification.Category.PAYMENT, "Hoàn tiền thành công",
                    "Khoản hoàn " + amount + " cho đơn " + shortCode(bookingId)
                            + " đã được xử lý thành công.",
                    bookingAction(bookingId), "BOOKING", bookingId.toString(), true);
        } else {
            draft = new NotificationService.NotificationDraft(
                    inbox.getEventId(), recipient.getAuthUserId(), "REFUND_MANUAL_REQUIRED",
                    Notification.Category.PAYMENT, "Yêu cầu hoàn tiền đang được kiểm tra",
                    "Khoản hoàn " + amount + " cho đơn " + shortCode(bookingId)
                            + " cần được đội ngũ Việt Khám Phá kiểm tra thủ công.",
                    bookingAction(bookingId), "BOOKING", bookingId.toString(), true);
        }
        notificationService.create(draft);
        return true;
    }

    private NotificationService.NotificationDraft bookingDraft(
            UUID eventId, String eventType, UUID bookingId, UUID authUserId, Map<String, Object> payload) {
        String code = shortCode(bookingId);
        String amount = formatAmount(payload.get("totalAmount"));
        String action = bookingAction(bookingId);
        return switch (eventType) {
            case "booking.created" -> new NotificationService.NotificationDraft(
                    eventId, authUserId, "BOOKING_HOLD_CREATED", Notification.Category.BOOKING,
                    "Đã giữ chỗ cho chuyến đi",
                    "Đơn " + code + " đã được giữ chỗ đến " + formatInstant(payload.get("holdExpiresAt"))
                            + ". Tổng tiền dự kiến: " + amount + ".",
                    action, "BOOKING", bookingId.toString(), false);
            case "booking.confirmed" -> new NotificationService.NotificationDraft(
                    eventId, authUserId, "BOOKING_CONFIRMED", Notification.Category.PAYMENT,
                    "Đặt tour đã được xác nhận",
                    "Thanh toán thành công. Đơn " + code + " trị giá " + amount
                            + " đã được xác nhận" + departureSuffix(payload) + ".",
                    action, "BOOKING", bookingId.toString(), true);
            case "booking.payment_failed" -> new NotificationService.NotificationDraft(
                    eventId, authUserId, "BOOKING_PAYMENT_FAILED", Notification.Category.PAYMENT,
                    "Thanh toán chưa thành công",
                    "Thanh toán cho đơn " + code
                            + " chưa thành công và chỗ tạm giữ đã được giải phóng.",
                    action, "BOOKING", bookingId.toString(), true);
            case "booking.expired" -> new NotificationService.NotificationDraft(
                    eventId, authUserId, "BOOKING_EXPIRED", Notification.Category.BOOKING,
                    "Đã hết thời gian giữ chỗ",
                    "Đơn " + code + " đã hết thời gian thanh toán và chỗ tạm giữ đã được giải phóng.",
                    action, "BOOKING", bookingId.toString(), false);
            case "booking.cancelled" -> cancelledDraft(eventId, bookingId, authUserId, payload, action, code);
            case "booking.late_payment_recovered" -> new NotificationService.NotificationDraft(
                    eventId, authUserId, "LATE_PAYMENT_RECOVERED", Notification.Category.PAYMENT,
                    "Thanh toán muộn đã được ghi nhận",
                    "Đơn " + code + " đã được khôi phục và xác nhận sau khi hệ thống nhận thanh toán.",
                    action, "BOOKING", bookingId.toString(), true);
            case "booking.late_payment_refund_required" -> new NotificationService.NotificationDraft(
                    eventId, authUserId, "LATE_PAYMENT_REFUND_REQUIRED", Notification.Category.PAYMENT,
                    "Đang xử lý hoàn tiền",
                    "Hệ thống đã nhận thanh toán cho đơn " + code
                            + " sau khi hết chỗ. Yêu cầu hoàn tiền đang được xử lý.",
                    action, "BOOKING", bookingId.toString(), true);
            case "booking.payment_review_required" -> new NotificationService.NotificationDraft(
                    eventId, authUserId, "PAYMENT_REVIEW_REQUIRED", Notification.Category.PAYMENT,
                    "Thanh toán cần được kiểm tra",
                    "Thanh toán cho đơn " + code
                            + " đến sau thời gian giữ chỗ và đang được đội ngũ Việt Khám Phá kiểm tra.",
                    action, "BOOKING", bookingId.toString(), true);
            default -> throw new PermanentEventException("Booking event không được hỗ trợ: " + eventType);
        };
    }

    private NotificationService.NotificationDraft cancelledDraft(
            UUID eventId, UUID bookingId, UUID authUserId, Map<String, Object> payload,
            String action, String code) {
        int refundPercentage = number(payload.get("refundPercentage"), 0).intValue();
        String refundMessage = refundPercentage > 0
                ? " Yêu cầu hoàn " + refundPercentage + "% đang được xử lý."
                : " Đơn này không phát sinh khoản hoàn tiền.";
        return new NotificationService.NotificationDraft(
                eventId, authUserId, "BOOKING_CANCELLED", Notification.Category.BOOKING,
                "Đặt tour đã được hủy",
                "Đơn " + code + " đã được hủy." + refundMessage,
                action, "BOOKING", bookingId.toString(), true);
    }

    private boolean isCustomerFacingBookingEvent(String eventType) {
        return switch (eventType) {
            case "booking.created", "booking.confirmed", "booking.payment_failed", "booking.expired",
                    "booking.cancelled", "booking.late_payment_recovered",
                    "booking.late_payment_refund_required", "booking.payment_review_required" -> true;
            default -> false;
        };
    }

    private String bookingStatus(String eventType) {
        return switch (eventType) {
            case "booking.created" -> "PENDING";
            case "booking.confirmed", "booking.late_payment_recovered" -> "CONFIRMED";
            case "booking.payment_failed" -> "PAYMENT_FAILED";
            case "booking.expired" -> "EXPIRED";
            case "booking.cancelled", "booking.late_payment_refund_required" -> "CANCELLED";
            case "booking.payment_review_required" -> "PAYMENT_REVIEW_REQUIRED";
            default -> "UNKNOWN";
        };
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> payload(Map<String, Object> event) {
        Object rawPayload = event.get("payload");
        if (!(rawPayload instanceof Map<?, ?>)) {
            throw new PermanentEventException("Event thiếu payload hợp lệ");
        }
        return (Map<String, Object>) rawPayload;
    }

    private UUID requiredUuid(Map<String, Object> payload, String field) {
        String value = text(payload, field);
        if (value == null) throw new PermanentEventException("Payload thiếu trường " + field);
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            throw new PermanentEventException("Payload có " + field + " không hợp lệ");
        }
    }

    private String text(Map<String, Object> payload, String field) {
        Object value = payload.get(field);
        if (value == null || value.toString().isBlank()) return null;
        return value.toString();
    }

    private LocalDate localDate(Map<String, Object> payload, String field) {
        String value = text(payload, field);
        if (value == null) return null;
        try {
            return LocalDate.parse(value);
        } catch (Exception exception) {
            throw new PermanentEventException("Payload có " + field + " không hợp lệ");
        }
    }

    private String departureSuffix(Map<String, Object> payload) {
        LocalDate startDate = localDate(payload, "startDate");
        return startDate == null ? "" : " cho ngày khởi hành " + DATE_FORMAT.format(startDate);
    }

    private String formatInstant(Object value) {
        if (value == null) return "thời hạn hiển thị trên đơn";
        try {
            return DATE_TIME_FORMAT.format(Instant.parse(value.toString()).atZone(VIETNAM_ZONE));
        } catch (Exception exception) {
            return "thời hạn hiển thị trên đơn";
        }
    }

    private String formatAmount(Object value) {
        if (value == null) return "số tiền trên đơn";
        try {
            BigDecimal amount = value instanceof BigDecimal decimal
                    ? decimal : new BigDecimal(value.toString());
            NumberFormat formatter = NumberFormat.getNumberInstance(Locale.forLanguageTag("vi-VN"));
            formatter.setMaximumFractionDigits(0);
            return formatter.format(amount) + " đ";
        } catch (Exception exception) {
            return "số tiền trên đơn";
        }
    }

    private Number number(Object value, Number fallback) {
        return value instanceof Number number ? number : fallback;
    }

    private String shortCode(UUID bookingId) {
        return "#" + bookingId.toString().substring(0, 8).toUpperCase(Locale.ROOT);
    }

    private String bookingAction(UUID bookingId) {
        return "/bookings/" + bookingId;
    }

    private void deferOrFail(NotificationEventInbox inbox, Instant now, String reason) {
        if (inbox.getAttemptCount() + 1 >= maxAttempts) {
            inbox.markFailed(now, reason);
            return;
        }
        long delaySeconds = Math.min(3600L, 10L * (1L << Math.min(inbox.getAttemptCount(), 8)));
        inbox.defer(now.plus(Duration.ofSeconds(delaySeconds)), reason);
    }

    private static class PermanentEventException extends RuntimeException {
        private PermanentEventException(String message) { super(message); }
    }

    private static class DeferredEventException extends RuntimeException {
        private DeferredEventException(String message) { super(message); }
    }
}
