package com.vietkhampha.notificationservice.job;

import com.vietkhampha.notificationservice.repository.BookingNotificationRecipientRepository;
import com.vietkhampha.notificationservice.service.DepartureReminderService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@Component
@ConditionalOnProperty(
        prefix = "app.notification.reminder",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class DepartureReminderJob {

    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final BookingNotificationRecipientRepository recipientRepository;
    private final DepartureReminderService reminderService;
    private final int daysBefore;

    public DepartureReminderJob(
            BookingNotificationRecipientRepository recipientRepository,
            DepartureReminderService reminderService,
            @Value("${app.notification.reminder.days-before:1}") int daysBefore
    ) {
        this.recipientRepository = recipientRepository;
        this.reminderService = reminderService;
        this.daysBefore = daysBefore;
    }

    @Scheduled(cron = "${app.notification.reminder.cron:0 0 8 * * *}", zone = "Asia/Ho_Chi_Minh")
    public void createDepartureReminders() {
        LocalDate departureDate = LocalDate.now(VIETNAM_ZONE).plusDays(daysBefore);
        while (true) {
            List<UUID> bookingIds = recipientRepository.findPendingReminderBookingIds(
                    departureDate, PageRequest.of(0, 100));
            if (bookingIds.isEmpty()) return;
            bookingIds.forEach(reminderService::createReminder);
        }
    }
}
