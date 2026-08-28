package com.vietkhampha.paymentservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vietkhampha.paymentservice.client.BookingServiceClient;
import com.vietkhampha.paymentservice.dto.CreatePaymentRequest;
import com.vietkhampha.paymentservice.dto.CreatePaymentResponse;
import com.vietkhampha.paymentservice.dto.VNPayIpnResponse;
import com.vietkhampha.paymentservice.dto.PaymentStatusResponse;
import com.vietkhampha.paymentservice.entity.OutboxEvent;
import com.vietkhampha.paymentservice.entity.Payment;
import com.vietkhampha.paymentservice.entity.PaymentIdempotencyKey;
import com.vietkhampha.paymentservice.entity.PaymentIdempotencyKeyId;
import com.vietkhampha.paymentservice.entity.PaymentLog;
import com.vietkhampha.paymentservice.entity.Refund;
import com.vietkhampha.paymentservice.exception.BusinessException;
import com.vietkhampha.paymentservice.exception.ErrorCode;
import com.vietkhampha.paymentservice.repository.OutboxEventRepository;
import com.vietkhampha.paymentservice.repository.PaymentIdempotencyKeyRepository;
import com.vietkhampha.paymentservice.repository.PaymentLogRepository;
import com.vietkhampha.paymentservice.repository.PaymentRepository;
import com.vietkhampha.paymentservice.repository.RefundRepository;
import com.vietkhampha.paymentservice.vnpay.VNPayService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final BookingServiceClient bookingServiceClient;
    private final VNPayService vnPayService;
    private final ObjectMapper objectMapper;
    private final RefundRepository refundRepository;
    private final PaymentLogRepository paymentLogRepository;
    private final PaymentIdempotencyKeyRepository paymentIdempotencyKeyRepository;
    private final PaymentRequestHasher paymentRequestHasher;
    private final TransactionTemplate transactionTemplate;

    private static final Duration IDEMPOTENCY_KEY_RETENTION = Duration.ofHours(24);
    private static final Set<Payment.Status> PAYMENT_INITIATION_BLOCKING_STATUSES = Set.of(
            Payment.Status.INITIATED,
            Payment.Status.SUCCESS
    );

    public PaymentService(PaymentRepository paymentRepository, OutboxEventRepository outboxEventRepository,
                          BookingServiceClient bookingServiceClient, VNPayService vnPayService,
                          ObjectMapper objectMapper, RefundRepository refundRepository,
                          PaymentLogRepository paymentLogRepository,
                          PaymentIdempotencyKeyRepository paymentIdempotencyKeyRepository,
                          PaymentRequestHasher paymentRequestHasher,
                          PlatformTransactionManager transactionManager) {
        this.paymentRepository = paymentRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.bookingServiceClient = bookingServiceClient;
        this.vnPayService = vnPayService;
        this.objectMapper = objectMapper;
        this.refundRepository = refundRepository;
        this.paymentLogRepository = paymentLogRepository;
        this.paymentIdempotencyKeyRepository = paymentIdempotencyKeyRepository;
        this.paymentRequestHasher = paymentRequestHasher;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public record PaymentResult(CreatePaymentResponse response, boolean replay) {
    }

    @Transactional(readOnly = true)
    public PaymentStatusResponse getPayment(UUID paymentId, String userIdHeader) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));
        bookingServiceClient.getBooking(payment.getBookingId(), userIdHeader);
        return PaymentStatusResponse.from(payment);
    }

    public PaymentResult createPayment(
            String userIdHeader,
            String idempotencyKey,
            CreatePaymentRequest request,
            String ipAddress
    ) {
        UUID customerId = parseCustomerId(userIdHeader);
        validateIdempotencyKey(idempotencyKey);

        Instant requestTime = Instant.now();
        String requestHash = paymentRequestHasher.hash(customerId, request);
        PaymentIdempotencyKeyId keyId = new PaymentIdempotencyKeyId(customerId, idempotencyKey);

        Optional<PaymentIdempotencyKey> existingKey = paymentIdempotencyKeyRepository.findById(keyId);
        if (existingKey.isPresent()) {
            return replayExisting(existingKey.get(), request.getBookingId(), requestHash, requestTime);
        }

        BookingServiceClient.BookingInfo bookingInfo = bookingServiceClient.getBooking(
                request.getBookingId(),
                userIdHeader
        );

        if (!request.getBookingId().equals(bookingInfo.bookingId())) {
            throw new IllegalStateException("Booking Service returned a different bookingId");
        }

        if (!"PENDING".equals(bookingInfo.status())) {
            throw new BusinessException(ErrorCode.BOOKING_NOT_PENDING);
        }
        ensurePaymentWindowOpen(bookingInfo.holdExpiresAt(), requestTime);

        VNPayService.PaymentUrlResult result = vnPayService.createPaymentUrl(
                bookingInfo.totalAmount(),
                "Thanh toan booking " + bookingInfo.bookingId(),
                ipAddress,
                bookingInfo.holdExpiresAt()
        );

        Instant keyExpiresAt = requestTime.plus(IDEMPOTENCY_KEY_RETENTION);
        if (keyExpiresAt.isBefore(bookingInfo.holdExpiresAt())) {
            keyExpiresAt = bookingInfo.holdExpiresAt();
        }
        Instant finalKeyExpiresAt = keyExpiresAt;

        try {
            return Objects.requireNonNull(transactionTemplate.execute(status -> createPaymentTransaction(
                    customerId,
                    idempotencyKey,
                    requestHash,
                    requestTime,
                    bookingInfo,
                    result,
                    finalKeyExpiresAt
            )));
        } catch (DataIntegrityViolationException exception) {
            if (paymentRepository.existsByBookingIdAndStatusIn(
                    bookingInfo.bookingId(),
                    PAYMENT_INITIATION_BLOCKING_STATUSES
            )) {
                throw new BusinessException(ErrorCode.PAYMENT_ALREADY_INITIATED);
            }
            throw exception;
        }
    }

    private PaymentResult createPaymentTransaction(
            UUID customerId,
            String idempotencyKey,
            String requestHash,
            Instant requestTime,
            BookingServiceClient.BookingInfo bookingInfo,
            VNPayService.PaymentUrlResult paymentUrl,
            Instant keyExpiresAt
    ) {
        PaymentIdempotencyKeyId keyId = new PaymentIdempotencyKeyId(customerId, idempotencyKey);
        int claimed = paymentIdempotencyKeyRepository.tryClaim(
                customerId,
                idempotencyKey,
                requestHash,
                PaymentRequestHasher.HASH_VERSION,
                requestTime,
                bookingInfo.holdExpiresAt(),
                keyExpiresAt
        );
        if (claimed == 0) {
            PaymentIdempotencyKey existingKey = paymentIdempotencyKeyRepository.findById(keyId)
                    .orElseThrow(() -> new IllegalStateException(
                            "Payment idempotency claim conflict resolved without a persisted record"
                    ));
            return replayExisting(existingKey, bookingInfo.bookingId(), requestHash, Instant.now());
        }

        PaymentIdempotencyKey claimedKey = paymentIdempotencyKeyRepository.findById(keyId)
                .orElseThrow(() -> new IllegalStateException("Created payment idempotency claim cannot be loaded"));

        int lockResult = paymentRepository.acquireBookingInitiationLock(bookingInfo.bookingId());
        if (lockResult != 1) {
            throw new IllegalStateException("Cannot acquire booking payment initiation lock");
        }

        ensurePaymentWindowOpen(bookingInfo.holdExpiresAt(), Instant.now());
        if (paymentRepository.existsByBookingIdAndStatusIn(
                bookingInfo.bookingId(),
                PAYMENT_INITIATION_BLOCKING_STATUSES
        )) {
            throw new BusinessException(ErrorCode.PAYMENT_ALREADY_INITIATED);
        }

        Payment payment = new Payment(
                bookingInfo.bookingId(),
                Payment.Gateway.VNPAY,
                bookingInfo.totalAmount()
        );
        payment.assignTransactionRef(paymentUrl.transactionRef());
        Payment savedPayment = paymentRepository.saveAndFlush(payment);

        publishPaymentInitiatedEvent(savedPayment);

        CreatePaymentResponse response = new CreatePaymentResponse(
                savedPayment.getId(),
                paymentUrl.redirectUrl()
        );
        claimedKey.complete(
                bookingInfo.bookingId(),
                savedPayment.getId(),
                serializeResponseSnapshot(response)
        );
        paymentIdempotencyKeyRepository.save(claimedKey);
        return new PaymentResult(response, false);
    }

    private PaymentResult replayExisting(
            PaymentIdempotencyKey existingKey,
            UUID requestedBookingId,
            String requestHash,
            Instant requestTime
    ) {
        if (!requestTime.isBefore(existingKey.getReplayExpiresAt())) {
            throw new BusinessException(ErrorCode.IDEMPOTENCY_KEY_EXPIRED);
        }
        if (!PaymentRequestHasher.HASH_VERSION.equals(existingKey.getHashVersion())
                || !requestHash.equals(existingKey.getRequestHash())) {
            throw new BusinessException(ErrorCode.IDEMPOTENCY_KEY_REUSED);
        }
        if (existingKey.getRecordState() != PaymentIdempotencyKey.RecordState.COMPLETED
                || existingKey.getBookingId() == null
                || existingKey.getPaymentId() == null
                || existingKey.getResponseSnapshot() == null) {
            throw new IllegalStateException("Committed payment idempotency record is not replayable");
        }
        if (!requestedBookingId.equals(existingKey.getBookingId())
                || !paymentRepository.existsByIdAndBookingId(
                        existingKey.getPaymentId(),
                        existingKey.getBookingId()
                )) {
            throw new IllegalStateException("Payment idempotency record does not match its payment");
        }

        try {
            CreatePaymentResponse response = objectMapper.readValue(
                    existingKey.getResponseSnapshot(),
                    CreatePaymentResponse.class
            );
            if (!existingKey.getPaymentId().equals(response.getPaymentId())) {
                throw new IllegalStateException("Payment idempotency snapshot paymentId does not match its record");
            }
            return new PaymentResult(response, true);
        } catch (BusinessException | IllegalStateException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot deserialize payment idempotency response snapshot", exception);
        }
    }

    private String serializeResponseSnapshot(CreatePaymentResponse response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot serialize payment idempotency response snapshot", exception);
        }
    }

    private void validateIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 255) {
            throw new BusinessException(ErrorCode.IDEMPOTENCY_KEY_INVALID);
        }
    }

    private UUID parseCustomerId(String userIdHeader) {
        try {
            return UUID.fromString(userIdHeader);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Trusted X-User-Id is not a UUID", exception);
        }
    }

    private void ensurePaymentWindowOpen(Instant holdExpiresAt, Instant now) {
        if (holdExpiresAt == null || !holdExpiresAt.isAfter(now.plusSeconds(1))) {
            throw new BusinessException(ErrorCode.PAYMENT_WINDOW_EXPIRED);
        }
    }

    private void publishPaymentInitiatedEvent(Payment payment) {
        try {
            String payload = objectMapper.writeValueAsString(Map.of(
                    "paymentId", payment.getId().toString(),
                    "bookingId", payment.getBookingId().toString(),
                    "gatewayTransactionRef", payment.getGatewayTransactionRef(),
                    "amount", payment.getAmount()
            ));
            OutboxEvent event = new OutboxEvent("PAYMENT", payment.getId(), "payment.initiated", payload);
            outboxEventRepository.save(event);
        } catch (Exception e) {
            throw new IllegalStateException("Loi serialize outbox event", e);
        }
    }

    @Transactional
    public VNPayIpnResponse processVnPayIpn(Map<String, String> params) {
        String txnRef = params.get("vnp_TxnRef");
        if (txnRef == null || txnRef.isBlank()) {
            return VNPayIpnResponse.invalidRequest();
        }

        Optional<Payment> paymentResult = paymentRepository.findByGatewayTransactionRefForUpdate(txnRef);
        if (paymentResult.isEmpty()) {
            return VNPayIpnResponse.orderNotFound();
        }

        Payment payment = paymentResult.get();
        if (!vnPayService.isExpectedTmnCode(params.get("vnp_TmnCode"))
                || payment.getGateway() != Payment.Gateway.VNPAY
                || !"VND".equals(payment.getCurrency())) {
            return VNPayIpnResponse.invalidRequest();
        }

        if (!matchesVnPayAmount(payment.getAmount(), params.get("vnp_Amount"))) {
            return VNPayIpnResponse.invalidAmount();
        }

        String responseCode = params.get("vnp_ResponseCode");
        String transactionStatus = params.get("vnp_TransactionStatus");
        if (responseCode == null || responseCode.isBlank()
                || transactionStatus == null || transactionStatus.isBlank()) {
            return VNPayIpnResponse.invalidRequest();
        }

        if (payment.getStatus() != Payment.Status.INITIATED) {
            return VNPayIpnResponse.alreadyConfirmed();
        }

        boolean successful = "00".equals(responseCode) && "00".equals(transactionStatus);
        if (successful) {
            payment.markSuccess();
        } else {
            payment.markFailed();
        }

        paymentRepository.save(payment);
        publishPaymentEvent(payment, successful ? "payment.succeeded" : "payment.failed");
        saveIpnLog(payment, params);
        return VNPayIpnResponse.success();
    }

    private boolean matchesVnPayAmount(BigDecimal paymentAmount, String receivedAmount) {
        if (receivedAmount == null || !receivedAmount.matches("[0-9]{1,12}")) {
            return false;
        }

        try {
            BigInteger expectedAmount = paymentAmount.movePointRight(2).toBigIntegerExact();
            return expectedAmount.signum() > 0 && expectedAmount.equals(new BigInteger(receivedAmount));
        } catch (ArithmeticException e) {
            return false;
        }
    }

    private void saveIpnLog(Payment payment, Map<String, String> params) {
        try {
            String rawPayload = objectMapper.writeValueAsString(params);
            paymentLogRepository.save(new PaymentLog(payment.getId(), "WEBHOOK_IPN", rawPayload));
        } catch (Exception e) {
            throw new IllegalStateException("Loi serialize VNPay IPN payload", e);
        }
    }

    private void publishPaymentEvent(Payment payment, String eventType) {
        try {
            String payload = objectMapper.writeValueAsString(Map.of(
                    "paymentId", payment.getId().toString(),
                    "bookingId", payment.getBookingId().toString(),
                    "gateway", payment.getGateway().name(),
                    "amount", payment.getAmount(),
                    "gatewayTransactionRef", payment.getGatewayTransactionRef()
            ));
            OutboxEvent event = new OutboxEvent("PAYMENT", payment.getId(), eventType, payload);
            outboxEventRepository.save(event);
        } catch (Exception e) {
            throw new IllegalStateException("Loi serialize outbox event", e);
        }
    }

    @Transactional
    public void processRefund(UUID bookingId, int refundPercentage, String ipAddress) {
        Payment payment = paymentRepository.findFirstByBookingIdAndStatusOrderByCreatedAtDesc(bookingId, Payment.Status.SUCCESS)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));

        if (refundRepository.existsByPaymentId(payment.getId())) {
            return;
        }

        BigDecimal refundAmount = payment.getAmount()
                .multiply(BigDecimal.valueOf(refundPercentage))
                .divide(BigDecimal.valueOf(100));

        Refund refund = new Refund(payment.getId(), refundAmount, refundPercentage);
        Refund savedRefund = refundRepository.save(refund);

        String originalTransactionDate = payment.getCreatedAt()
                .atZone(java.time.ZoneId.of("Asia/Ho_Chi_Minh"))
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));

        VNPayService.RefundResult result = vnPayService.createRefundRequest(
                payment.getGatewayTransactionRef(), refundAmount, originalTransactionDate, ipAddress
        );

        if (result.success()) {
            savedRefund.markSuccess(result.gatewayRefundRef());
            refundRepository.save(savedRefund);
            publishRefundEvent(savedRefund, payment, "refund.completed");
        } else {
            savedRefund.markManualRequired();
            refundRepository.save(savedRefund);
            publishRefundEvent(savedRefund, payment, "refund.manual_required");
        }
    }

    private void publishRefundEvent(Refund refund, Payment payment, String eventType) {
        try {
            String payload = objectMapper.writeValueAsString(Map.of(
                    "refundId", refund.getId().toString(),
                    "bookingId", payment.getBookingId().toString(),
                    "amount", refund.getAmount(),
                    "status", refund.getStatus().name()
            ));
            OutboxEvent event = new OutboxEvent("REFUND", refund.getId(), eventType, payload);
            outboxEventRepository.save(event);
        } catch (Exception e) {
            throw new IllegalStateException("Loi serialize outbox event", e);
        }
    }
}
