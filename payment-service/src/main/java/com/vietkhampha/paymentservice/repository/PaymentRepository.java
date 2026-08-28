package com.vietkhampha.paymentservice.repository;

import com.vietkhampha.paymentservice.entity.Payment;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    @Query(value = """
            SELECT 1
            FROM pg_advisory_xact_lock(
                hashtextextended(CAST(:bookingId AS text), 0)
            )
            """, nativeQuery = true)
    int acquireBookingInitiationLock(@Param("bookingId") UUID bookingId);

    Optional<Payment> findByGatewayTransactionRef(String ref);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select payment from Payment payment where payment.gatewayTransactionRef = :ref")
    Optional<Payment> findByGatewayTransactionRefForUpdate(@Param("ref") String ref);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Payment> findFirstByBookingIdAndStatusOrderByCreatedAtDesc(UUID bookingId, Payment.Status status);

    boolean existsByBookingIdAndStatusIn(UUID bookingId, Collection<Payment.Status> statuses);

    boolean existsByIdAndBookingId(UUID id, UUID bookingId);
}
