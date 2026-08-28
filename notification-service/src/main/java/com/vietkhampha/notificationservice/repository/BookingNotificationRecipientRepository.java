package com.vietkhampha.notificationservice.repository;

import com.vietkhampha.notificationservice.entity.BookingNotificationRecipient;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BookingNotificationRecipientRepository
        extends JpaRepository<BookingNotificationRecipient, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select recipient from BookingNotificationRecipient recipient where recipient.bookingId = :bookingId")
    Optional<BookingNotificationRecipient> findByIdForUpdate(@Param("bookingId") UUID bookingId);

    @Query("""
            select recipient.bookingId from BookingNotificationRecipient recipient
            where recipient.bookingStatus = 'CONFIRMED'
              and recipient.startDate = :departureDate
              and recipient.reminderSentAt is null
            order by recipient.bookingId
            """)
    List<UUID> findPendingReminderBookingIds(@Param("departureDate") LocalDate departureDate,
                                             Pageable pageable);
}
