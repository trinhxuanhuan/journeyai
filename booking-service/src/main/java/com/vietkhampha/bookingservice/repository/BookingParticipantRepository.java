package com.vietkhampha.bookingservice.repository;

import com.vietkhampha.bookingservice.entity.BookingParticipant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface BookingParticipantRepository extends JpaRepository<BookingParticipant, UUID> {

}