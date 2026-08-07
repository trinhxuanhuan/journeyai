package com.vietkhampha.bookingservice.repository;

import com.vietkhampha.bookingservice.entity.Booking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface BookingRepository extends JpaRepository<Booking, UUID> {
    List<Booking> findByStatusAndHoldExpiresAtBefore(Booking.Status status, Instant now);
    @Query("""
        SELECT b FROM Booking b JOIN TourSlot s ON b.tourSlotId = s.id
        WHERE (:tourId IS NULL OR s.tourId = :tourId)
        AND (:departureDate IS NULL OR s.departureDate = :departureDate)
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
