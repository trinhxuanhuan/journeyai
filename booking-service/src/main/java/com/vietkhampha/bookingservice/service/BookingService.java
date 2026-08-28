package com.vietkhampha.bookingservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vietkhampha.bookingservice.client.TourServiceClient;
import com.vietkhampha.bookingservice.dto.CreateBookingRequest;
import com.vietkhampha.bookingservice.dto.CreateBookingResponse;
import com.vietkhampha.bookingservice.dto.BookingResponse;
import com.vietkhampha.bookingservice.dto.CustomerBookingItemResponse;
import com.vietkhampha.bookingservice.dto.CustomerBookingListResponse;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
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
    private final com.vietkhampha.bookingservice.event.DepartureEventPublisher departureEventPublisher;

    private static final long LATE_PAYMENT_GRACE_PERIOD_MINUTES = 15;
    private static final Duration IDEMPOTENCY_TTL = Duration.ofHours(24);

    public BookingService(BookingRepository bookingRepository, TourSlotRepository tourSlotRepository,
                          IdempotencyKeyRepository idempotencyKeyRepository, OutboxEventRepository outboxEventRepository,
                          TourServiceClient tourServiceClient, ObjectMapper objectMapper,
                          BookingStateMachineService stateMachineService,
                          BookingRequestHasher bookingRequestHasher,
                          com.vietkhampha.bookingservice.event.DepartureEventPublisher departureEventPublisher) {
        this.bookingRepository = bookingRepository;
        this.tourSlotRepository = tourSlotRepository;
        this.idempotencyKeyRepository = idempotencyKeyRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.tourServiceClient = tourServiceClient;
        this.objectMapper = objectMapper;
        this.stateMachineService = stateMachineService;
        this.bookingRequestHasher = bookingRequestHasher;
        this.departureEventPublisher = departureEventPublisher;
    }

    public record BookingResult(CreateBookingResponse response, boolean isReplay) {}

    @Transactional(readOnly = true)
    public CustomerBookingListResponse getCustomerBookings(UUID customerId, int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new BusinessException(ErrorCode.PAGINATION_INVALID);
        }

        Sort sort = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
        Page<Booking> result = bookingRepository.findByCustomerId(
                customerId,
                PageRequest.of(page, size, sort)
        );
        var items = result.getContent().stream()
                .map(CustomerBookingItemResponse::from)
                .toList();

        return new CustomerBookingListResponse(
                items,
                result.getTotalElements(),
                result.getNumber(),
                result.getSize(),
                result.getTotalPages()
        );
    }

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
            return replayExisting(existingKey, customerId, request, requestHash, requestTime);
        }

        IdempotencyKey claimedKey = idempotencyKeyRepository.findById(idempotencyKeyId)
                .orElseThrow(() -> new IllegalStateException("Created idempotency claim cannot be loaded"));

        int participantCount = request.getParticipants().size();
        BookingQuote quote = quoteBooking(request, participantCount);

        if (quote.departure() != null) {
            quote.departure().reserve(participantCount);
            tourSlotRepository.save(quote.departure());
            departureEventPublisher.publishUpdated(quote.departure());
        }

        Booking booking = new Booking(
                customerId,
                quote.tour().id(),
                quote.bookingType(),
                quote.departure() == null ? null : quote.departure().getId(),
                quote.startDate(),
                quote.endDate(),
                participantCount,
                quote.priceModel(),
                quote.unitPrice(),
                quote.totalAmount(),
                quote.commercialSnapshot(),
                quote.cancellationPolicySnapshot(),
                request.isGuideOptionSelected(),
                request.getSingleRoomCount()
        );
        for (ParticipantDto p : request.getParticipants()) {
            booking.addParticipant(new BookingParticipant(
                    p.getFullName(), p.getPhone(), p.isPrimaryContact(), p.getParticipantType()
            ));
        }
        Booking savedBooking = bookingRepository.save(booking);

        publishBookingCreatedEvent(savedBooking);

        CreateBookingResponse response = toResponse(savedBooking);
        claimedKey.complete(savedBooking.getId(), serializeResponseSnapshot(response));

        return new BookingResult(response, false);
    }

    private BookingQuote quoteBooking(CreateBookingRequest request, int participantCount) {
        if (participantCount < 1 || request.getSingleRoomCount() > participantCount) {
            throw new BusinessException(ErrorCode.BOOKING_REQUEST_INVALID);
        }
        long primaryContacts = request.getParticipants().stream().filter(ParticipantDto::isPrimaryContact).count();
        if (primaryContacts != 1) {
            throw new BusinessException(ErrorCode.BOOKING_REQUEST_INVALID);
        }

        TourSlot departure = null;
        TourServiceClient.TourInfo tour;
        Booking.BookingType bookingType;
        LocalDate startDate;
        LocalDate endDate;
        BigDecimal unitPrice;

        if (request.getDepartureId() != null) {
            departure = tourSlotRepository.findByIdForUpdate(request.getDepartureId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.TOUR_SLOT_NOT_FOUND));
            if (request.getTourId() != null && !request.getTourId().isBlank()
                    && !request.getTourId().equals(departure.getTourId())) {
                throw new BusinessException(ErrorCode.BOOKING_REQUEST_INVALID);
            }
            tour = tourServiceClient.requireActiveTour(departure.getTourId());
            if (!"GROUP".equals(tour.tourType())) {
                throw new BusinessException(ErrorCode.BOOKING_REQUEST_INVALID);
            }
            if (departure.getGuideId() == null || departure.getGuideId().isBlank()) {
                throw new BusinessException(ErrorCode.DEPARTURE_CONFIGURATION_INVALID);
            }
            if (!departure.hasCapacityFor(participantCount)) {
                throw new BusinessException(ErrorCode.SLOT_UNAVAILABLE);
            }
            bookingType = Booking.BookingType.GROUP;
            startDate = departure.getStartDate();
            endDate = departure.getEndDate();
            unitPrice = departure.getPriceOverride() == null ? tour.basePrice() : departure.getPriceOverride();
        } else {
            if (request.getTourId() == null || request.getTourId().isBlank()) {
                throw new BusinessException(ErrorCode.BOOKING_REQUEST_INVALID);
            }
            tour = tourServiceClient.requireActiveTour(request.getTourId());
            if (!"PRIVATE".equals(tour.tourType())) {
                throw new BusinessException(ErrorCode.BOOKING_REQUEST_INVALID);
            }
            if (request.getRequestedStartDate() == null
                    || request.getRequestedStartDate().isBefore(LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")))) {
                throw new BusinessException(ErrorCode.PRIVATE_START_DATE_INVALID);
            }
            bookingType = Booking.BookingType.PRIVATE;
            startDate = request.getRequestedStartDate();
            endDate = startDate.plusDays(Math.max(0, tour.durationDays() - 1L));
            unitPrice = tour.basePrice();
        }

        if (participantCount < tour.minGroupSize() || participantCount > tour.maxGroupSize()) {
            throw new BusinessException(ErrorCode.GROUP_SIZE_INVALID);
        }
        if (request.isGuideOptionSelected() && !"OPTIONAL".equals(tour.guideMode())) {
            throw new BusinessException(ErrorCode.BOOKING_REQUEST_INVALID);
        }

        Booking.PriceModel priceModel;
        try {
            priceModel = Booking.PriceModel.valueOf(tour.priceModel());
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.TOUR_SERVICE_INVALID_RESPONSE);
        }
        validateCommercialConfiguration(tour, bookingType, priceModel, unitPrice);

        long adultCount = request.getParticipants().stream()
                .filter(participant -> participant.getParticipantType() == BookingParticipant.ParticipantType.ADULT)
                .count();
        long childCount = participantCount - adultCount;

        BigDecimal packageAmount;
        if (priceModel == Booking.PriceModel.PER_GROUP) {
            packageAmount = unitPrice;
        } else {
            BigDecimal adultAmount = unitPrice.multiply(BigDecimal.valueOf(adultCount));
            BigDecimal childUnitPrice = unitPrice
                    .multiply(tour.childPricePercentage())
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            packageAmount = adultAmount.add(childUnitPrice.multiply(BigDecimal.valueOf(childCount)));
        }

        BigDecimal singleRoomAmount = tour.singleRoomSupplement()
                .multiply(BigDecimal.valueOf(request.getSingleRoomCount()));
        BigDecimal guideAmount = request.isGuideOptionSelected()
                ? tour.optionalGuidePrice()
                : BigDecimal.ZERO;
        BigDecimal totalAmount = packageAmount.add(singleRoomAmount).add(guideAmount)
                .setScale(2, RoundingMode.HALF_UP);

        String cancellationPolicySnapshot = serialize(tour.cancellationPolicy());
        String commercialSnapshot = commercialSnapshot(
                tour, bookingType, departure, startDate, endDate, priceModel, unitPrice,
                adultCount, childCount, packageAmount, singleRoomAmount, guideAmount, totalAmount
        );

        return new BookingQuote(
                tour, bookingType, departure, startDate, endDate, priceModel,
                unitPrice, totalAmount, commercialSnapshot, cancellationPolicySnapshot
        );
    }

    private void validateCommercialConfiguration(TourServiceClient.TourInfo tour,
                                                 Booking.BookingType bookingType,
                                                 Booking.PriceModel priceModel,
                                                 BigDecimal unitPrice) {
        if (unitPrice == null || unitPrice.signum() <= 0
                || tour.minGroupSize() < 1 || tour.maxGroupSize() < tour.minGroupSize()
                || tour.durationDays() < 1
                || tour.childPricePercentage() == null
                || tour.childPricePercentage().signum() < 0
                || tour.childPricePercentage().compareTo(BigDecimal.valueOf(100)) > 0
                || tour.singleRoomSupplement() == null || tour.singleRoomSupplement().signum() < 0
                || tour.optionalGuidePrice() == null || tour.optionalGuidePrice().signum() < 0) {
            throw new BusinessException(ErrorCode.TOUR_SERVICE_INVALID_RESPONSE);
        }
        if (bookingType == Booking.BookingType.GROUP
                && (priceModel != Booking.PriceModel.PER_PERSON || !"INCLUDED".equals(tour.guideMode()))) {
            throw new BusinessException(ErrorCode.TOUR_SERVICE_INVALID_RESPONSE);
        }
    }

    private String commercialSnapshot(TourServiceClient.TourInfo tour, Booking.BookingType bookingType,
                                      TourSlot departure, LocalDate startDate, LocalDate endDate,
                                      Booking.PriceModel priceModel, BigDecimal unitPrice,
                                      long adultCount, long childCount, BigDecimal packageAmount,
                                      BigDecimal singleRoomAmount, BigDecimal guideAmount,
                                      BigDecimal totalAmount) {
        Map<String, Object> snapshot = new LinkedHashMap<>(tour.commercialData());
        snapshot.remove("tourGuideId");
        snapshot.put("bookingType", bookingType.name());
        snapshot.put("departureId", departure == null ? null : departure.getId().toString());
        snapshot.put("startDate", startDate.toString());
        snapshot.put("endDate", endDate.toString());

        Map<String, Object> priceBreakdown = new LinkedHashMap<>();
        priceBreakdown.put("priceModel", priceModel.name());
        priceBreakdown.put("unitPrice", unitPrice);
        priceBreakdown.put("adultCount", adultCount);
        priceBreakdown.put("childCount", childCount);
        priceBreakdown.put("childPricePercentage", tour.childPricePercentage());
        priceBreakdown.put("packageAmount", packageAmount);
        priceBreakdown.put("singleRoomSupplementAmount", singleRoomAmount);
        priceBreakdown.put("optionalGuideAmount", guideAmount);
        priceBreakdown.put("totalAmount", totalAmount);
        snapshot.put("priceBreakdown", priceBreakdown);
        return serialize(snapshot);
    }

    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("Khong the tao commercial snapshot", exception);
        }
    }

    private record BookingQuote(
            TourServiceClient.TourInfo tour,
            Booking.BookingType bookingType,
            TourSlot departure,
            LocalDate startDate,
            LocalDate endDate,
            Booking.PriceModel priceModel,
            BigDecimal unitPrice,
            BigDecimal totalAmount,
            String commercialSnapshot,
            String cancellationPolicySnapshot
    ) {}

    private BookingResult replayExisting(IdempotencyKey existingKey, UUID customerId,
                                         CreateBookingRequest request, String requestHash, Instant requestTime) {
        if (existingKey.getRecordState() == IdempotencyKey.RecordState.LEGACY_EXPIRED
                || !requestTime.isBefore(existingKey.getExpiresAt())) {
            throw new BusinessException(ErrorCode.IDEMPOTENCY_KEY_EXPIRED);
        }

        String expectedHash = BookingRequestHasher.HASH_VERSION.equals(existingKey.getHashVersion())
                ? requestHash
                : bookingRequestHasher.hashForVersion(customerId, request, existingKey.getHashVersion());
        if (expectedHash == null || !expectedHash.equals(existingKey.getRequestHash())) {
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
            Map<String, Object> eventPayload = new LinkedHashMap<>();
            eventPayload.put("bookingId", booking.getId().toString());
            eventPayload.put("customerId", booking.getCustomerId().toString());
            eventPayload.put("tourId", booking.getTourId());
            eventPayload.put("bookingType", booking.getBookingType().name());
            eventPayload.put("departureId", booking.getDepartureId() == null
                    ? null : booking.getDepartureId().toString());
            eventPayload.put("tourSlotId", booking.getTourSlotId() == null
                    ? null : booking.getTourSlotId().toString());
            eventPayload.put("startDate", booking.getStartDate().toString());
            eventPayload.put("endDate", booking.getEndDate().toString());
            eventPayload.put("totalAmount", booking.getTotalAmount());
            eventPayload.put("holdExpiresAt", booking.getHoldExpiresAt().toString());
            String payload = objectMapper.writeValueAsString(eventPayload);
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

        if (booking.usesSharedCapacity()) {
            TourSlot slot = tourSlotRepository.findByIdForUpdate(booking.getTourSlotId())
                    .orElseThrow();
            slot.release(booking.getParticipantCount());
            tourSlotRepository.save(slot);
            departureEventPublisher.publishUpdated(slot);
        }

        stateMachineService.transition(booking, event);
        bookingRepository.save(booking);

        publishBookingTerminatedEvent(booking, eventType, reason);
    }

    private void publishBookingTerminatedEvent(Booking booking, String eventType, String reason) {
        try {
            String payload = objectMapper.writeValueAsString(Map.of(
                    "bookingId", booking.getId().toString(),
                    "customerId", booking.getCustomerId().toString(),
                    "tourId", booking.getTourId(),
                    "startDate", booking.getStartDate().toString(),
                    "endDate", booking.getEndDate().toString(),
                    "totalAmount", booking.getTotalAmount(),
                    "reason", reason,
                    "refundEligible", false
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

        if (!booking.usesSharedCapacity()) {
            stateMachineService.transition(booking, BookingEvent.LATE_PAYMENT_RECOVERED);
            bookingRepository.save(booking);
            publishLatePaymentRecoveredEvent(booking);
            return;
        }

        TourSlot slot = tourSlotRepository.findByIdForUpdate(booking.getTourSlotId()).orElseThrow();

        if (slot.hasCapacityFor(booking.getParticipantCount())) {
            slot.reserve(booking.getParticipantCount());
            tourSlotRepository.save(slot);
            departureEventPublisher.publishUpdated(slot);

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
                    "tourId", booking.getTourId(),
                    "startDate", booking.getStartDate().toString(),
                    "endDate", booking.getEndDate().toString(),
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
                    "tourId", booking.getTourId(),
                    "startDate", booking.getStartDate().toString(),
                    "endDate", booking.getEndDate().toString(),
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
                    "tourId", booking.getTourId(),
                    "startDate", booking.getStartDate().toString(),
                    "endDate", booking.getEndDate().toString(),
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
                    "tourId", booking.getTourId(),
                    "startDate", booking.getStartDate().toString(),
                    "endDate", booking.getEndDate().toString(),
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

        long daysUntilDeparture = java.time.temporal.ChronoUnit.DAYS.between(
                LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")),
                booking.getStartDate()
        );
        if (daysUntilDeparture < 0) {
            throw new BusinessException(ErrorCode.BOOKING_CANCEL_WINDOW_CLOSED);
        }

        int refundPercentage = calculateRefundPercentage(booking, daysUntilDeparture);

        if (booking.usesSharedCapacity()) {
            TourSlot slot = tourSlotRepository.findByIdForUpdate(booking.getTourSlotId()).orElseThrow();
            slot.release(booking.getParticipantCount());
            tourSlotRepository.save(slot);
            departureEventPublisher.publishUpdated(slot);
        }

        stateMachineService.transition(booking, BookingEvent.CUSTOMER_CANCEL);
        bookingRepository.save(booking);

        publishBookingCancelledEvent(booking, refundPercentage, "Khách hàng chủ động hủy");
    }

    @Transactional
    public BookingResponse assignPrivateGuide(UUID bookingId, String guideId) {
        Booking booking = bookingRepository.findByIdForUpdate(bookingId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BOOKING_NOT_FOUND));
        if (booking.getBookingType() != Booking.BookingType.PRIVATE) {
            throw new BusinessException(ErrorCode.BOOKING_REQUEST_INVALID);
        }
        TourServiceClient.TourInfo tour = tourServiceClient.requireActiveTour(booking.getTourId());
        if ("NONE".equals(tour.guideMode())
                || ("OPTIONAL".equals(tour.guideMode()) && !booking.isGuideOptionSelected())) {
            throw new BusinessException(ErrorCode.BOOKING_REQUEST_INVALID);
        }
        tourServiceClient.requireActiveGuide(guideId);
        booking.assignGuide(guideId);
        return BookingResponse.from(bookingRepository.save(booking));
    }

    @Transactional
    public void cancelDeparture(UUID departureId) {
        TourSlot departure = tourSlotRepository.findByIdForUpdate(departureId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SLOT_NOT_FOUND));
        if (departure.getStatus() == TourSlot.Status.CANCELLED) return;
        if (departure.getStatus() == TourSlot.Status.COMPLETED) {
            throw new BusinessException(ErrorCode.DEPARTURE_CONFIGURATION_INVALID);
        }

        for (Booking booking : bookingRepository.findByTourSlotId(departureId)) {
            if (booking.getStatus() != Booking.Status.PENDING
                    && booking.getStatus() != Booking.Status.CONFIRMED) {
                continue;
            }
            boolean paid = booking.getStatus() == Booking.Status.CONFIRMED;
            departure.release(booking.getParticipantCount());
            stateMachineService.transition(booking, BookingEvent.CUSTOMER_CANCEL);
            bookingRepository.save(booking);
            publishBookingCancelledEvent(booking, paid ? 100 : 0, "Lịch khởi hành bị hủy");
        }
        departure.applyUpdate(null, null, null, null, null, TourSlot.Status.CANCELLED);
        tourSlotRepository.save(departure);
        departureEventPublisher.publishUpdated(departure);
    }

    @Transactional
    public void completeDeparture(UUID departureId) {
        TourSlot departure = tourSlotRepository.findByIdForUpdate(departureId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SLOT_NOT_FOUND));
        if (departure.getStatus() == TourSlot.Status.COMPLETED) return;
        if (departure.getStatus() == TourSlot.Status.CANCELLED) {
            throw new BusinessException(ErrorCode.DEPARTURE_CONFIGURATION_INVALID);
        }
        if (departure.getEndDate().isAfter(LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")))) {
            throw new BusinessException(ErrorCode.DEPARTURE_CONFIGURATION_INVALID);
        }
        for (Booking booking : bookingRepository.findByTourSlotId(departureId)) {
            if (booking.getStatus() == Booking.Status.CONFIRMED) {
                stateMachineService.transition(booking, BookingEvent.TOUR_COMPLETED);
                bookingRepository.save(booking);
            }
        }
        departure.applyUpdate(null, null, null, null, null, TourSlot.Status.COMPLETED);
        tourSlotRepository.save(departure);
        departureEventPublisher.publishUpdated(departure);
    }

    @SuppressWarnings("unchecked")
    private int calculateRefundPercentage(Booking booking, long daysUntilDeparture) {
        try {
            List<Map<String, Object>> rules = objectMapper.readValue(
                    booking.getCancellationPolicySnapshot(), List.class
            );
            return rules.stream()
                    .filter(rule -> daysUntilDeparture >= ((Number) rule.get("minimumDaysBeforeDeparture")).longValue())
                    .map(rule -> ((Number) rule.get("refundPercentage")).intValue())
                    .findFirst()
                    .orElse(0);
        } catch (Exception exception) {
            throw new IllegalStateException("Cancellation policy snapshot khong hop le", exception);
        }
    }

    private void publishBookingCancelledEvent(Booking booking, int refundPercentage, String reason) {
        try {
            String payload = objectMapper.writeValueAsString(Map.of(
                    "bookingId", booking.getId().toString(),
                    "customerId", booking.getCustomerId().toString(),
                    "tourId", booking.getTourId(),
                    "startDate", booking.getStartDate().toString(),
                    "endDate", booking.getEndDate().toString(),
                    "reason", reason,
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
