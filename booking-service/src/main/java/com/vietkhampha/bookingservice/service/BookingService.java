package com.vietkhampha.bookingservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vietkhampha.bookingservice.client.TourServiceClient;
import com.vietkhampha.bookingservice.dto.CreateBookingRequest;
import com.vietkhampha.bookingservice.dto.CreateBookingResponse;
import com.vietkhampha.bookingservice.dto.ParticipantDto;
import com.vietkhampha.bookingservice.entity.*;
import com.vietkhampha.bookingservice.exception.BusinessException;
import com.vietkhampha.bookingservice.exception.ErrorCode;
import com.vietkhampha.bookingservice.repository.BookingRepository;
import com.vietkhampha.bookingservice.repository.IdempotencyKeyRepository;
import com.vietkhampha.bookingservice.repository.OutboxEventRepository;
import com.vietkhampha.bookingservice.repository.TourSlotRepository;
import com.vietkhampha.bookingservice.statemachine.BookingEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.vietkhampha.bookingservice.statemachine.BookingStateMachineService;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class BookingService {

    private final BookingRepository bookingRepository;
    private final TourSlotRepository tourSlotRepository;
    private final IdempotencyKeyRepository idempotencyKeyRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final TourServiceClient tourServiceClient;
    private final ObjectMapper objectMapper;
    private final BookingStateMachineService stateMachineService;

    public BookingService(BookingRepository bookingRepository, TourSlotRepository tourSlotRepository,
                          IdempotencyKeyRepository idempotencyKeyRepository, OutboxEventRepository outboxEventRepository,
                          TourServiceClient tourServiceClient, ObjectMapper objectMapper,
                          BookingStateMachineService stateMachineService) {
        this.bookingRepository = bookingRepository;
        this.tourSlotRepository = tourSlotRepository;
        this.idempotencyKeyRepository = idempotencyKeyRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.tourServiceClient = tourServiceClient;
        this.objectMapper = objectMapper;
        this.stateMachineService = stateMachineService;
    }

    public record BookingResult(CreateBookingResponse response, boolean isReplay) {}

    @Transactional
    public BookingResult createBooking(UUID customerId, String idempotencyKey, CreateBookingRequest request) {


        Optional<IdempotencyKey> existingKey = idempotencyKeyRepository.findById(idempotencyKey);
        if (existingKey.isPresent()) {
            Booking existingBooking = bookingRepository.findById(existingKey.get().getBookingId())
                    .orElseThrow(() -> new IllegalStateException("Idempotency key tro toi booking khong ton tai"));
            return new BookingResult(toResponse(existingBooking), true);
        }

        TourSlot slot = tourSlotRepository.findByIdForUpdate(request.getTourSlotId())
                .orElseThrow(() -> new BusinessException(ErrorCode.TOUR_SLOT_NOT_FOUND));

        int participantCount = request.getParticipants().size();
        if (!slot.hasCapacityFor(participantCount)) {
            throw new BusinessException(ErrorCode.SLOT_UNAVAILABLE);
        }

        BigDecimal basePrice = tourServiceClient.getTourBasePrice(slot.getTourId());
        BigDecimal totalAmount = basePrice.multiply(BigDecimal.valueOf(participantCount));

        slot.reserve(participantCount);
        tourSlotRepository.save(slot);

        Booking booking = new Booking(customerId, slot.getId(), participantCount, totalAmount);
        booking.setGeneratedItineraryId(request.getGeneratedItineraryId());
        for (ParticipantDto p : request.getParticipants()) {
            booking.addParticipant(new BookingParticipant(p.getFullName(), p.getPhone(), p.isPrimaryContact()));
        }
        Booking savedBooking = bookingRepository.save(booking);

        idempotencyKeyRepository.save(new IdempotencyKey(idempotencyKey, savedBooking.getId()));

        publishBookingCreatedEvent(savedBooking);

        return new BookingResult(toResponse(savedBooking), false);
    }

    private void publishBookingCreatedEvent(Booking booking) {
        try {
            String payload = objectMapper.writeValueAsString(Map.of(
                    "bookingId", booking.getId().toString(),
                    "customerId", booking.getCustomerId().toString(),
                    "tourSlotId", booking.getTourSlotId().toString(),
                    "totalAmount", booking.getTotalAmount(),
                    "holdExpiresAt", booking.getHoldExpiresAt().toString()
            ));
            OutboxEvent event = new OutboxEvent("BOOKING", booking.getId(), "booking.created", payload);
            outboxEventRepository.save(event);
        } catch (Exception e) {
            throw new IllegalStateException("Loi serialize outbox event", e);
        }
    }

    private CreateBookingResponse toResponse(Booking booking) {
        return new CreateBookingResponse(booking.getId(), booking.getStatus().name(),
                booking.getTotalAmount(), booking.getHoldExpiresAt());
    }

    @Transactional
    public void expireBooking(UUID bookingId, String reason) {
        transitionAndReleaseSlot(bookingId, BookingEvent.HOLD_TIMEOUT, reason, "booking.expired");
    }

    @Transactional
    public void failBookingPayment(UUID bookingId, String reason) {
        transitionAndReleaseSlot(bookingId, BookingEvent.PAYMENT_FAILED, reason, "booking.payment_failed");
    }

    private void transitionAndReleaseSlot(UUID bookingId, BookingEvent event, String reason, String eventType) {
        Booking booking = bookingRepository.findById(bookingId).orElseThrow();

        if (booking.getStatus() != Booking.Status.PENDING) {
            return;
        }

        TourSlot slot = tourSlotRepository.findByIdForUpdate(booking.getTourSlotId())
                .orElseThrow();
        slot.release(booking.getParticipantCount());
        tourSlotRepository.save(slot);

        stateMachineService.transition(booking, event);
        bookingRepository.save(booking);

        publishBookingTerminatedEvent(booking, eventType, reason);
    }

    private void publishBookingTerminatedEvent(Booking booking, String eventType, String reason) {
        try {
            String payload = objectMapper.writeValueAsString(Map.of(
                    "bookingId", booking.getId().toString(),
                    "customerId", booking.getCustomerId().toString(),
                    "reason", reason,
                    "refundEligible", false // Chưa có Payment Service thật — mặc định chưa hoàn tiền, sẽ cập nhật khi tích hợp VNPay
            ));
            OutboxEvent outboxEvent = new OutboxEvent("BOOKING", booking.getId(), eventType, payload);
            outboxEventRepository.save(outboxEvent);
        } catch (Exception e) {
            throw new IllegalStateException("Loi serialize outbox event", e);
        }
    }
    @Transactional
    public void confirmBookingPayment(UUID bookingId) {
        Booking booking = bookingRepository.findById(bookingId).orElseThrow();

        if (booking.getStatus() != Booking.Status.PENDING) {
            return;
        }

        stateMachineService.transition(booking, BookingEvent.PAYMENT_CONFIRMED);
        bookingRepository.save(booking);

        publishBookingConfirmedEvent(booking);
    }

    private void publishBookingConfirmedEvent(Booking booking) {
        try {
            String payload = objectMapper.writeValueAsString(Map.of(
                    "bookingId", booking.getId().toString(),
                    "customerId", booking.getCustomerId().toString(),
                    "totalAmount", booking.getTotalAmount()
            ));
            OutboxEvent event = new OutboxEvent("BOOKING", booking.getId(), "booking.confirmed", payload);
            outboxEventRepository.save(event);
        } catch (Exception e) {
            throw new IllegalStateException("Loi serialize outbox event", e);
        }
    }
}