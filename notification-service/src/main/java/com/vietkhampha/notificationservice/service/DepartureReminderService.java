package com.vietkhampha.notificationservice.service;

import com.vietkhampha.notificationservice.entity.BookingNotificationRecipient;
import com.vietkhampha.notificationservice.entity.Notification;
import com.vietkhampha.notificationservice.repository.BookingNotificationRecipientRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

@Service
public class DepartureReminderService {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final BookingNotificationRecipientRepository recipientRepository;
    private final NotificationService notificationService;

    public DepartureReminderService(BookingNotificationRecipientRepository recipientRepository,
                                    NotificationService notificationService) {
        this.recipientRepository = recipientRepository;
        this.notificationService = notificationService;
    }

    @Transactional
    public void createReminder(UUID bookingId) {
        BookingNotificationRecipient recipient = recipientRepository.findByIdForUpdate(bookingId).orElse(null);
        if (recipient == null || recipient.getReminderSentAt() != null
                || !"CONFIRMED".equals(recipient.getBookingStatus())
                || recipient.getStartDate() == null) {
            return;
        }

        UUID eventId = UUID.nameUUIDFromBytes(("departure.reminder|" + bookingId + "|"
                + recipient.getStartDate()).getBytes(StandardCharsets.UTF_8));
        String code = "#" + bookingId.toString().substring(0, 8).toUpperCase(Locale.ROOT);
        notificationService.create(new NotificationService.NotificationDraft(
                eventId,
                recipient.getAuthUserId(),
                "DEPARTURE_REMINDER",
                Notification.Category.DEPARTURE,
                "Chuyến đi của bạn sắp khởi hành",
                "Đơn " + code + " sẽ khởi hành vào ngày "
                        + DATE_FORMAT.format(recipient.getStartDate())
                        + ". Hãy kiểm tra điểm hẹn và chuẩn bị giấy tờ cần thiết.",
                "/bookings/" + bookingId,
                "BOOKING",
                bookingId.toString(),
                true
        ));
        recipient.markReminderSent(Instant.now());
        recipientRepository.save(recipient);
    }
}
