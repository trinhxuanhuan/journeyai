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
import java.util.UUID;
import java.time.Duration;
import java.time.Instant;

@Service
public class BookingService {

    private final BookingRepository bookingRepository;
    private final TourSlotRepository tourSlotRepository;
    private final IdempotencyKeyRepository idempotencyKeyRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final TourServiceClient tourServiceClient;
    private final ObjectMapper objectMapper;
    private final BookingStateMachineService stateMachineService;
    private final BookingRequestHasher bookingRequestHasher;

    private static final long LATE_PAYMENT_GRACE_PERIOD_MINUTES = 15;
    private static final Duration IDEMPOTENCY_TTL = Duration.ofHours(24);

    public BookingService(BookingRepository bookingRepository, TourSlotRepository tourSlotRepository,
                          IdempotencyKeyRepository idempotencyKeyRepository, OutboxEventRepository outboxEventRepository,
                          TourServiceClient tourServiceClient, ObjectMapper objectMapper,
                          BookingStateMachineService stateMachineService,
                          BookingRequestHasher bookingRequestHasher) {
        this.bookingRepository = bookingRepository;
        this.tourSlotRepository = tourSlotRepository;
        this.idempotencyKeyRepository = idempotencyKeyRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.tourServiceClient = tourServiceClient;
        this.objectMapper = objectMapper;
        this.stateMachineService = stateMachineService;
        this.bookingRequestHasher = bookingRequestHasher;
    }

    public record BookingResult(CreateBookingResponse response, boolean isReplay) {}

    @Transactional
    public BookingResult createBooking(UUID customerId, String idempotencyKey, CreateBookingRequest request) {
        validateIdempotencyKey(idempotencyKey);

        Instant requestTime = Instant.now();
        String requestHash = bookingRequestHasher.hash(customerId, request);
        IdempotencyKeyId idempotencyKeyId = new IdempotencyKeyId(customerId, idempotencyKey);

        int claimed = idempotencyKeyRepository.tryClaim(
                customerId,
                idempotencyKey,
                requestHash,
                BookingRequestHasher.HASH_VERSION,
                requestTime,
                requestTime.plus(IDEMPOTENCY_TTL)
        );
        if (claimed == 0) {
            IdempotencyKey existingKey = idempotencyKeyRepository.findById(idempotencyKeyId)
                    .orElseThrow(() -> new IllegalStateException(
                            "Idempotency claim conflict resolved without a persisted record"
                    ));
            return replayExisting(existingKey, requestHash, requestTime);
        }

        IdempotencyKey claimedKey = idempotencyKeyRepository.findById(idempotencyKeyId)
                .orElseThrow(() -> new IllegalStateException("Created idempotency claim cannot be loaded"));

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

        publishBookingCreatedEvent(savedBooking);

        CreateBookingResponse response = toResponse(savedBooking);
        claimedKey.complete(savedBooking.getId(), serializeResponseSnapshot(response));

        return new BookingResult(response, false);
    }

    private BookingResult replayExisting(IdempotencyKey existingKey, String requestHash, Instant requestTime) {
        if (existingKey.getRecordState() == IdempotencyKey.RecordState.LEGACY_EXPIRED
                || !requestTime.isBefore(existingKey.getExpiresAt())) {
            throw new BusinessException(ErrorCode.IDEMPOTENCY_KEY_EXPIRED);
        }

        if (!BookingRequestHasher.HASH_VERSION.equals(existingKey.getHashVersion())
                || !requestHash.equals(existingKey.getRequestHash())) {
            throw new BusinessException(ErrorCode.IDEMPOTENCY_KEY_REUSED);
        }

        if (existingKey.getRecordState() != IdempotencyKey.RecordState.COMPLETED
                || existingKey.getBookingId() == null
                || existingKey.getResponseSnapshot() == null) {
            throw new IllegalStateException("Committed idempotency record is not replayable");
        }
        if (!bookingRepository.existsByIdAndCustomerId(
                existingKey.getBookingId(),
                existingKey.getCustomerId()
        )) {
            throw new IllegalStateException("Idempotency record does not belong to its booking owner");
        }

        try {
            CreateBookingResponse response = objectMapper.readValue(
                    existingKey.getResponseSnapshot(),
                    CreateBookingResponse.class
            );
            if (!existingKey.getBookingId().equals(response.getBookingId())) {
                throw new IllegalStateException("Idempotency snapshot bookingId does not match its record");
            }
            return new BookingResult(response, true);
        } catch (BusinessException | IllegalStateException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot deserialize idempotency response snapshot", exception);
        }
    }

    private String serializeResponseSnapshot(CreateBookingResponse response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot serialize idempotency response snapshot", exception);
        }
    }

    private void validateIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 255) {
            throw new BusinessException(ErrorCode.IDEMPOTENCY_KEY_INVALID);
        }
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
        Booking booking = bookingRepository.findByIdForUpdate(bookingId).orElseThrow();

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
        Booking booking = bookingRepository.findByIdForUpdate(bookingId).orElseThrow();

        if (booking.getStatus() == Booking.Status.PENDING) {
            stateMachineService.transition(booking, BookingEvent.PAYMENT_CONFIRMED);
            bookingRepository.save(booking);
            publishBookingConfirmedEvent(booking);
            return;
        }

        if (booking.getStatus() == Booking.Status.EXPIRED) {
            handleLatePayment(booking);
            return;
        }

    }

    private void handleLatePayment(Booking booking) {
        Duration delay = Duration.between(booking.getHoldExpiresAt(), Instant.now());

        if (delay.toMinutes() > LATE_PAYMENT_GRACE_PERIOD_MINUTES) {
            stateMachineService.transition(booking, BookingEvent.LATE_PAYMENT_REVIEW);
            bookingRepository.save(booking);
            publishPaymentReviewRequiredEvent(booking, delay);
            return;
        }

        TourSlot slot = tourSlotRepository.findByIdForUpdate(booking.getTourSlotId()).orElseThrow();

        if (slot.hasCapacityFor(booking.getParticipantCount())) {
            slot.reserve(booking.getParticipantCount());
            tourSlotRepository.save(slot);

            stateMachineService.transition(booking, BookingEvent.LATE_PAYMENT_RECOVERED);
            bookingRepository.save(booking);

            publishLatePaymentRecoveredEvent(booking);
        } else {
            publishLatePaymentRefundRequiredEvent(booking);
        }
    }

    private void publishLatePaymentRecoveredEvent(Booking booking) {
        try {
            String payload = objectMapper.writeValueAsString(Map.of(
                    "eventId", UUID.randomUUID().toString(),
                    "bookingId", booking.getId().toString(),
                    "customerId", booking.getCustomerId().toString(),
                    "totalAmount", booking.getTotalAmount(),
                    "recoveredAt", Instant.now().toString()
            ));
            OutboxEvent event = new OutboxEvent("BOOKING", booking.getId(), "booking.late_payment_recovered", payload);
            outboxEventRepository.save(event);
        } catch (Exception e) {
            throw new IllegalStateException("Loi serialize outbox event", e);
        }
    }

    private void publishLatePaymentRefundRequiredEvent(Booking booking) {
        try {
            String payload = objectMapper.writeValueAsString(Map.of(
                    "eventId", UUID.randomUUID().toString(),
                    "bookingId", booking.getId().toString(),
                    "customerId", booking.getCustomerId().toString(),
                    "totalAmount", booking.getTotalAmount(),
                    "refundPercentage", 100,
                    "reason", "LATE_PAYMENT_SLOT_UNAVAILABLE"
            ));
            OutboxEvent event = new OutboxEvent("BOOKING", booking.getId(), "booking.late_payment_refund_required", payload);
            outboxEventRepository.save(event);
        } catch (Exception e) {
            throw new IllegalStateException("Loi serialize outbox event", e);
        }
    }

    private void publishPaymentReviewRequiredEvent(Booking booking, Duration delay) {
        try {
            String payload = objectMapper.writeValueAsString(Map.of(
                    "eventId", UUID.randomUUID().toString(),
                    "bookingId", booking.getId().toString(),
                    "customerId", booking.getCustomerId().toString(),
                    "totalAmount", booking.getTotalAmount(),
                    "delayMinutes", delay.toMinutes()
            ));
            OutboxEvent event = new OutboxEvent("BOOKING", booking.getId(), "booking.payment_review_required", payload);
            outboxEventRepository.save(event);
        } catch (Exception e) {
            throw new IllegalStateException("Loi serialize outbox event", e);
        }
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

    @Transactional
    public void cancelBooking(UUID bookingId, UUID customerId) {
        Booking booking = bookingRepository.findByIdForUpdate(bookingId).orElseThrow(
                () -> new BusinessException(ErrorCode.BOOKING_NOT_FOUND));

        if (!booking.getCustomerId().equals(customerId)) {
            throw new BusinessException(ErrorCode.BOOKING_NOT_FOUND);
        }
        if (booking.getStatus() != Booking.Status.CONFIRMED) {
            throw new BusinessException(ErrorCode.BOOKING_NOT_CANCELLABLE);
        }

        TourSlot slot = tourSlotRepository.findByIdForUpdate(booking.getTourSlotId()).orElseThrow();
        long hoursUntilDeparture = java.time.Duration.between(
                java.time.Instant.now(),
                slot.getDepartureDate().atStartOfDay(java.time.ZoneId.of("Asia/Ho_Chi_Minh")).toInstant()
        ).toHours();
        if (hoursUntilDeparture < 24) {
            throw new BusinessException(ErrorCode.BOOKING_CANCEL_WINDOW_CLOSED);
        }

        int refundPercentage = calculateRefundPercentage(hoursUntilDeparture);

        slot.release(booking.getParticipantCount());
        tourSlotRepository.save(slot);

        stateMachineService.transition(booking, BookingEvent.CUSTOMER_CANCEL);
        bookingRepository.save(booking);

        publishBookingCancelledEvent(booking, refundPercentage);
    }

    private int calculateRefundPercentage(long hoursUntilDeparture) {
        long daysUntilDeparture = hoursUntilDeparture / 24;
        if (daysUntilDeparture >= 7) return 100;
        if (daysUntilDeparture >= 3) return 50;
        return 0;
    }

    private void publishBookingCancelledEvent(Booking booking, int refundPercentage) {
        try {
            String payload = objectMapper.writeValueAsString(Map.of(
                    "bookingId", booking.getId().toString(),
                    "customerId", booking.getCustomerId().toString(),
                    "reason", "Khach hang chu dong huy",
                    "refundEligible", refundPercentage > 0,
                    "refundPercentage", refundPercentage
            ));
            OutboxEvent event = new OutboxEvent("BOOKING", booking.getId(), "booking.cancelled", payload);
            outboxEventRepository.save(event);
        } catch (Exception e) {
            throw new IllegalStateException("Loi serialize outbox event", e);
        }
    }
}
