package com.vietkhampha.notificationservice.repository;

import com.vietkhampha.notificationservice.entity.NotificationEmailDelivery;
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

public interface NotificationEmailDeliveryRepository extends JpaRepository<NotificationEmailDelivery, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select delivery from NotificationEmailDelivery delivery where delivery.id = :deliveryId")
    Optional<NotificationEmailDelivery> findByIdForUpdate(@Param("deliveryId") UUID deliveryId);

    @Query("""
            select delivery.id from NotificationEmailDelivery delivery
            where delivery.status in (
                com.vietkhampha.notificationservice.entity.NotificationEmailDelivery.Status.PENDING,
                com.vietkhampha.notificationservice.entity.NotificationEmailDelivery.Status.WAITING_RECIPIENT
            )
              and delivery.nextAttemptAt <= :now
            order by delivery.createdAt asc
            """)
    List<UUID> findDispatchableIds(@Param("now") Instant now, Pageable pageable);
}
