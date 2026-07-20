package com.vietkhampha.bookingservice.repository;

import com.vietkhampha.bookingservice.entity.IdempotencyKey;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, String> {

}
