package com.vietkhampha.bookingservice.repository;

import com.vietkhampha.bookingservice.entity.TourSlot;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface TourSlotRepository extends JpaRepository<TourSlot, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM TourSlot s WHERE s.tourId = :tourId AND s.departureDate = :departureDate")
    Optional<TourSlot> findByTourIdAndDepartureDateForUpdate(
            @Param("tourId") String tourId,
            @Param("departureDate") LocalDate departureDate
    );

    Optional<TourSlot> findByTourIdAndDepartureDate(String tourId, LocalDate departureDate);
}