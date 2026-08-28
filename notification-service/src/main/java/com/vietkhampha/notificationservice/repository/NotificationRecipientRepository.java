package com.vietkhampha.notificationservice.repository;

import com.vietkhampha.notificationservice.entity.NotificationRecipient;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface NotificationRecipientRepository extends JpaRepository<NotificationRecipient, UUID> {
}
