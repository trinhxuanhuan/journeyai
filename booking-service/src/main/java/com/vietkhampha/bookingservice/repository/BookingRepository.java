package com.vietkhampha.bookingservice.repository;

import com.vietkhampha.bookingservice.entity.Booking;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface BookingRepository extends JpaRepository<Booking, UUID> {

}
