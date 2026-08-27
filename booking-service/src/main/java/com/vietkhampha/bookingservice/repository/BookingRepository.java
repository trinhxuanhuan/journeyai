package com.vietkhampha.bookingservice.repository;

import com.vietkhampha.bookingservice.entity.Booking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface BookingRepository extends JpaRepository<Booking, UUID> {
    boolean existsByIdAndCustomerId(UUID id, UUID customerId);

    Page<Booking> findByCustomerId(UUID customerId, Pageable pageable);

    List<Booking> findByStatusAndHoldExpiresAtBefore(Booking.Status status, Instant now);
    List<Booking> findByTourSlotId(UUID tourSlotId);
    @Query("""
        SELECT b FROM Booking b
        WHERE (:tourId IS NULL OR b.tourId = :tourId)
        AND (:departureDate IS NULL OR b.startDate = :departureDate)
        AND (:status IS NULL OR b.status = :status)
        ORDER BY b.createdAt DESC
        """)
    org.springframework.data.domain.Page<Booking> findByFilters(
            @org.springframework.data.repository.query.Param("tourId") String tourId,
            @org.springframework.data.repository.query.Param("departureDate") java.time.LocalDate departureDate,
            @org.springframework.data.repository.query.Param("status") Booking.Status status,
            org.springframework.data.domain.Pageable pageable
    );
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("SELECT b FROM Booking b WHERE b.id = :id")
    java.util.Optional<Booking> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") UUID id);
}
