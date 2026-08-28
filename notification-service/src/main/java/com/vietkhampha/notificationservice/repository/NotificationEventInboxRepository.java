package com.vietkhampha.notificationservice.repository;

import com.vietkhampha.notificationservice.entity.NotificationEventInbox;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationEventInboxRepository extends JpaRepository<NotificationEventInbox, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select event from NotificationEventInbox event where event.eventId = :eventId")
    Optional<NotificationEventInbox> findByIdForUpdate(@Param("eventId") UUID eventId);

    @Query("""
            select event.eventId from NotificationEventInbox event
            where event.status = com.vietkhampha.notificationservice.entity.NotificationEventInbox.Status.RECEIVED
               or (event.status = com.vietkhampha.notificationservice.entity.NotificationEventInbox.Status.WAITING
                   and event.nextAttemptAt <= :now)
            order by event.receivedAt asc
            """)
    List<UUID> findRetryableEventIds(@Param("now") Instant now, Pageable pageable);
}
