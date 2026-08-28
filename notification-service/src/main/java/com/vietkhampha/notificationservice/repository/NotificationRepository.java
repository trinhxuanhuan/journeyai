package com.vietkhampha.notificationservice.repository;

import com.vietkhampha.notificationservice.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {
    Page<Notification> findByAuthUserId(UUID authUserId, Pageable pageable);
    Page<Notification> findByAuthUserIdAndReadAtIsNull(UUID authUserId, Pageable pageable);
    Page<Notification> findByAuthUserIdAndReadAtIsNotNull(UUID authUserId, Pageable pageable);
    Optional<Notification> findByIdAndAuthUserId(UUID id, UUID authUserId);
    Optional<Notification> findByEventId(UUID eventId);
    long countByAuthUserIdAndReadAtIsNull(UUID authUserId);

    @Modifying
    @Query("""
            update Notification notification
            set notification.readAt = :readAt
            where notification.authUserId = :authUserId
              and notification.readAt is null
            """)
    int markAllRead(@Param("authUserId") UUID authUserId, @Param("readAt") Instant readAt);
}
