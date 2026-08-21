package com.vietkhampha.paymentservice.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vietkhampha.paymentservice.client.BookingServiceClient;
import com.vietkhampha.paymentservice.entity.Payment;
import com.vietkhampha.paymentservice.entity.PaymentIdempotencyKey;
import com.vietkhampha.paymentservice.entity.PaymentIdempotencyKeyId;
import com.vietkhampha.paymentservice.exception.BusinessException;
import com.vietkhampha.paymentservice.exception.ErrorCode;
import com.vietkhampha.paymentservice.repository.OutboxEventRepository;
import com.vietkhampha.paymentservice.repository.PaymentIdempotencyKeyRepository;
import com.vietkhampha.paymentservice.repository.PaymentLogRepository;
import com.vietkhampha.paymentservice.repository.PaymentRepository;
import com.vietkhampha.paymentservice.repository.RefundRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PaymentInitiationIntegrationTest {

    private static final BigDecimal BOOKING_AMOUNT = new BigDecimal("1250000.00");
    private static final String CUSTOMER_ID = "11111111-1111-1111-1111-111111111111";
    private static final String SECOND_CUSTOMER_ID = "33333333-3333-3333-3333-333333333333";
    private static final DateTimeFormatter VNPAY_DATE_TIME_FORMATTER = DateTimeFormatter
            .ofPattern("yyyyMMddHHmmss")
            .withZone(ZoneId.of("Asia/Ho_Chi_Minh"));

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("payment_initiation_test")
            .withUsername("payment_initiation_test")
            .withPassword("payment_initiation_test_password");

    @DynamicPropertySource
    static void configureTestEnvironment(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.kafka.listener.auto-startup", () -> "false");
        registry.add("spring.kafka.bootstrap-servers", () -> "localhost:1");
        registry.add("app.outbox.poller.enabled", () -> "false");
        registry.add("app.vnpay.tmn-code", () -> "TEST_MERCHANT");
        registry.add("app.vnpay.hash-secret", () -> "test-only-vnpay-hash-secret");
        registry.add("app.vnpay.pay-url", () -> "https://vnpay.test/pay");
        registry.add("app.vnpay.return-url", () -> "https://journeyai.test/vnpay-return");
        registry.add("app.booking-service.base-url", () -> "http://localhost:1");
    }

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private PaymentIdempotencyKeyRepository idempotencyKeyRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private PaymentLogRepository paymentLogRepository;

    @Autowired
    private RefundRepository refundRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private BookingServiceClient bookingServiceClient;

    private Instant holdExpiresAt;

    @BeforeEach
    void prepareTest() {
        idempotencyKeyRepository.deleteAll();
        paymentLogRepository.deleteAll();
        refundRepository.deleteAll();
        outboxEventRepository.deleteAll();
        paymentRepository.deleteAll();
        reset(bookingServiceClient);

        holdExpiresAt = Instant.now().plus(Duration.ofMinutes(10)).truncatedTo(ChronoUnit.SECONDS);
        when(bookingServiceClient.getBooking(any(UUID.class), anyString()))
                .thenAnswer(invocation -> bookingInfo(invocation.getArgument(0), "PENDING", holdExpiresAt));
    }

    @AfterEach
    void removeFailureInjectionConstraint() {
        jdbcTemplate.execute("ALTER TABLE outbox_events DROP CONSTRAINT IF EXISTS reject_payment_initiated_for_test");
    }

    @Test
    void firstRequestCreatesPaymentIdempotencySnapshotAndOutboxAtomically() throws Exception {
        UUID bookingId = UUID.randomUUID();

        ResponseEntity<String> response = createPayment(CUSTOMER_ID, "payment-key-1", bookingId, "VNPAY");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode body = objectMapper.readTree(response.getBody());
        UUID paymentId = UUID.fromString(body.get("paymentId").asText());
        assertThat(body.get("redirectUrl").asText()).startsWith("https://vnpay.test/pay?");
        assertThat(body.get("redirectUrl").asText())
                .contains("vnp_ExpireDate=" + VNPAY_DATE_TIME_FORMATTER.format(holdExpiresAt));

        Payment payment = paymentRepository.findById(paymentId).orElseThrow();
        assertThat(payment.getBookingId()).isEqualTo(bookingId);
        assertThat(payment.getAmount()).isEqualByComparingTo(BOOKING_AMOUNT);
        assertThat(payment.getStatus()).isEqualTo(Payment.Status.INITIATED);
        assertThat(payment.getGatewayTransactionRef()).isNotBlank();

        PaymentIdempotencyKey key = idempotencyKeyRepository.findById(
                new PaymentIdempotencyKeyId(UUID.fromString(CUSTOMER_ID), "payment-key-1")
        ).orElseThrow();
        assertThat(key.getRecordState()).isEqualTo(PaymentIdempotencyKey.RecordState.COMPLETED);
        assertThat(key.getBookingId()).isEqualTo(bookingId);
        assertThat(key.getPaymentId()).isEqualTo(paymentId);
        assertThat(key.getReplayExpiresAt()).isEqualTo(holdExpiresAt);
        assertThat(key.getResponseSnapshot()).contains(paymentId.toString());

        assertThat(outboxEventRepository.findAll())
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.getEventType()).isEqualTo("payment.initiated");
                    assertThat(event.getAggregateId()).isEqualTo(paymentId);
                });
    }

    @Test
    void sameCustomerKeyAndPayloadReplaysExactSnapshotWithoutCallingBookingAgain() throws Exception {
        UUID bookingId = UUID.randomUUID();
        String key = "payment-key-replay";
        ResponseEntity<String> first = createPayment(CUSTOMER_ID, key, bookingId, "VNPAY");

        ResponseEntity<String> replay = createPayment(CUSTOMER_ID, key, bookingId, "VNPAY");

        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(replay.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(objectMapper.readTree(replay.getBody())).isEqualTo(objectMapper.readTree(first.getBody()));
        assertThat(paymentRepository.count()).isEqualTo(1);
        assertThat(idempotencyKeyRepository.count()).isEqualTo(1);
        assertThat(outboxEventRepository.count()).isEqualTo(1);
        verify(bookingServiceClient, times(1)).getBooking(bookingId, CUSTOMER_ID);
    }

    @Test
    void expiredReplayWindowReturnsConflictAndNeverReusesTheKey() throws Exception {
        UUID bookingId = UUID.randomUUID();
        String key = "payment-key-expired-replay";
        createPayment(CUSTOMER_ID, key, bookingId, "VNPAY");
        jdbcTemplate.update("""
                WITH clock AS (SELECT CURRENT_TIMESTAMP AS now)
                UPDATE payment_idempotency_keys
                SET created_at = clock.now - INTERVAL '25 minutes',
                    replay_expires_at = clock.now - INTERVAL '5 minutes',
                    key_expires_at = clock.now - INTERVAL '25 minutes' + INTERVAL '24 hours'
                FROM clock
                WHERE customer_id = ?::uuid AND key = ?
                """, CUSTOMER_ID, key);

        ResponseEntity<String> response = createPayment(CUSTOMER_ID, key, bookingId, "VNPAY");

        assertError(response, HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_EXPIRED");
        assertThat(paymentRepository.count()).isEqualTo(1);
        assertThat(idempotencyKeyRepository.count()).isEqualTo(1);
        assertThat(outboxEventRepository.count()).isEqualTo(1);
        verify(bookingServiceClient, times(1)).getBooking(bookingId, CUSTOMER_ID);
    }

    @Test
    void sameKeyWithDifferentPayloadReturnsConflictWithoutMutation() throws Exception {
        createPayment(CUSTOMER_ID, "payment-key-reused", UUID.randomUUID(), "VNPAY");

        ResponseEntity<String> response = createPayment(
                CUSTOMER_ID,
                "payment-key-reused",
                UUID.randomUUID(),
                "VNPAY"
        );

        assertError(response, HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_REUSED");
        assertThat(paymentRepository.count()).isEqualTo(1);
        assertThat(idempotencyKeyRepository.count()).isEqualTo(1);
        assertThat(outboxEventRepository.count()).isEqualTo(1);
    }

    @Test
    void missingKeyAndUnsupportedGatewayAreRejectedBeforePersistence() throws Exception {
        UUID bookingId = UUID.randomUUID();

        ResponseEntity<String> missingKey = createPayment(CUSTOMER_ID, null, bookingId, "VNPAY");
        ResponseEntity<String> unsupportedGateway = createPayment(
                CUSTOMER_ID,
                "payment-key-stripe",
                bookingId,
                "STRIPE"
        );

        assertError(missingKey, HttpStatus.BAD_REQUEST, "IDEMPOTENCY_KEY_INVALID");
        assertError(unsupportedGateway, HttpStatus.BAD_REQUEST, "VALIDATION_FAILED");
        assertThat(paymentRepository.count()).isZero();
        assertThat(idempotencyKeyRepository.count()).isZero();
        assertThat(outboxEventRepository.count()).isZero();
    }

    @Test
    void nonPendingOrExpiredBookingCannotCreatePayment() throws Exception {
        UUID confirmedBooking = UUID.randomUUID();
        UUID expiredBooking = UUID.randomUUID();
        when(bookingServiceClient.getBooking(eq(confirmedBooking), eq(CUSTOMER_ID)))
                .thenReturn(bookingInfo(confirmedBooking, "CONFIRMED", holdExpiresAt));
        when(bookingServiceClient.getBooking(eq(expiredBooking), eq(CUSTOMER_ID)))
                .thenReturn(bookingInfo(expiredBooking, "PENDING", Instant.now().minusSeconds(60)));

        ResponseEntity<String> nonPending = createPayment(
                CUSTOMER_ID,
                "payment-key-confirmed",
                confirmedBooking,
                "VNPAY"
        );
        ResponseEntity<String> expired = createPayment(
                CUSTOMER_ID,
                "payment-key-expired-booking",
                expiredBooking,
                "VNPAY"
        );

        assertError(nonPending, HttpStatus.UNPROCESSABLE_ENTITY, "BOOKING_NOT_PENDING");
        assertError(expired, HttpStatus.CONFLICT, "PAYMENT_WINDOW_EXPIRED");
        assertThat(paymentRepository.count()).isZero();
        assertThat(idempotencyKeyRepository.count()).isZero();
    }

    @Test
    void differentKeyForBookingWithActivePaymentReturnsControlledConflict() throws Exception {
        UUID bookingId = UUID.randomUUID();
        createPayment(CUSTOMER_ID, "payment-key-active-1", bookingId, "VNPAY");

        ResponseEntity<String> response = createPayment(
                CUSTOMER_ID,
                "payment-key-active-2",
                bookingId,
                "VNPAY"
        );

        assertError(response, HttpStatus.CONFLICT, "PAYMENT_ALREADY_INITIATED");
        assertThat(paymentRepository.count()).isEqualTo(1);
        assertThat(idempotencyKeyRepository.count()).isEqualTo(1);
        assertThat(outboxEventRepository.count()).isEqualTo(1);
    }

    @Test
    void successfulPaymentBlocksASecondInitiationWhileBookingIsStillPending() throws Exception {
        UUID bookingId = UUID.randomUUID();
        Payment successfulPayment = new Payment(bookingId, Payment.Gateway.VNPAY, BOOKING_AMOUNT);
        successfulPayment.assignTransactionRef("SUCCESSFUL_PAYMENT_REF");
        successfulPayment.markSuccess();
        paymentRepository.saveAndFlush(successfulPayment);

        ResponseEntity<String> response = createPayment(
                CUSTOMER_ID,
                "payment-key-after-success",
                bookingId,
                "VNPAY"
        );

        assertError(response, HttpStatus.CONFLICT, "PAYMENT_ALREADY_INITIATED");
        assertThat(paymentRepository.count()).isEqualTo(1);
        assertThat(idempotencyKeyRepository.count()).isZero();
        assertThat(outboxEventRepository.count()).isZero();
    }

    @Test
    void sameKeyConcurrentRequestsCreateOnePaymentAndReplayOneSnapshot() throws Exception {
        UUID bookingId = UUID.randomUUID();
        int requestCount = 8;
        CyclicBarrier barrier = new CyclicBarrier(requestCount);
        ExecutorService executor = Executors.newFixedThreadPool(requestCount);

        try {
            List<Future<ResponseEntity<String>>> futures = new ArrayList<>();
            for (int index = 0; index < requestCount; index++) {
                futures.add(executor.submit(() -> {
                    barrier.await(10, TimeUnit.SECONDS);
                    return createPayment(CUSTOMER_ID, "payment-key-concurrent", bookingId, "VNPAY");
                }));
            }

            List<ResponseEntity<String>> responses = new ArrayList<>();
            for (Future<ResponseEntity<String>> future : futures) {
                responses.add(future.get(20, TimeUnit.SECONDS));
            }

            assertThat(responses).filteredOn(response -> response.getStatusCode() == HttpStatus.CREATED).hasSize(1);
            assertThat(responses).filteredOn(response -> response.getStatusCode() == HttpStatus.OK).hasSize(7);
            Set<JsonNode> bodies = new HashSet<>();
            for (ResponseEntity<String> response : responses) {
                bodies.add(objectMapper.readTree(response.getBody()));
            }
            assertThat(bodies).hasSize(1);
            assertThat(paymentRepository.count()).isEqualTo(1);
            assertThat(idempotencyKeyRepository.count()).isEqualTo(1);
            assertThat(outboxEventRepository.count()).isEqualTo(1);
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void differentKeysConcurrentRequestsStillCreateOnlyOneActivePayment() throws Exception {
        UUID bookingId = UUID.randomUUID();
        int requestCount = 8;
        CyclicBarrier barrier = new CyclicBarrier(requestCount);
        ExecutorService executor = Executors.newFixedThreadPool(requestCount);

        try {
            List<Future<ResponseEntity<String>>> futures = new ArrayList<>();
            for (int index = 0; index < requestCount; index++) {
                String key = "payment-key-race-" + index;
                futures.add(executor.submit(() -> {
                    barrier.await(10, TimeUnit.SECONDS);
                    return createPayment(CUSTOMER_ID, key, bookingId, "VNPAY");
                }));
            }

            List<ResponseEntity<String>> responses = new ArrayList<>();
            for (Future<ResponseEntity<String>> future : futures) {
                responses.add(future.get(20, TimeUnit.SECONDS));
            }

            assertThat(responses).filteredOn(response -> response.getStatusCode() == HttpStatus.CREATED).hasSize(1);
            assertThat(responses).filteredOn(response -> response.getStatusCode() == HttpStatus.CONFLICT).hasSize(7);
            for (ResponseEntity<String> response : responses) {
                if (response.getStatusCode() == HttpStatus.CONFLICT) {
                    assertThat(objectMapper.readTree(response.getBody()).get("error").asText())
                            .isEqualTo("PAYMENT_ALREADY_INITIATED");
                }
            }
            assertThat(paymentRepository.count()).isEqualTo(1);
            assertThat(idempotencyKeyRepository.count()).isEqualTo(1);
            assertThat(outboxEventRepository.count()).isEqualTo(1);
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void sameRawKeyForAnotherCustomerNeverReplaysOriginalPayment() throws Exception {
        UUID bookingId = UUID.randomUUID();
        String key = "payment-key-owned";
        createPayment(CUSTOMER_ID, key, bookingId, "VNPAY");
        when(bookingServiceClient.getBooking(eq(bookingId), eq(SECOND_CUSTOMER_ID)))
                .thenThrow(new BusinessException(ErrorCode.BOOKING_NOT_FOUND));

        ResponseEntity<String> response = createPayment(
                SECOND_CUSTOMER_ID,
                key,
                bookingId,
                "VNPAY"
        );

        assertError(response, HttpStatus.NOT_FOUND, "BOOKING_NOT_FOUND");
        assertThat(paymentRepository.count()).isEqualTo(1);
        assertThat(idempotencyKeyRepository.count()).isEqualTo(1);
        assertThat(idempotencyKeyRepository.findById(
                new PaymentIdempotencyKeyId(UUID.fromString(SECOND_CUSTOMER_ID), key)
        )).isEmpty();
    }

    @Test
    void outboxFailureRollsBackClaimPaymentAndOutbox() throws Exception {
        jdbcTemplate.execute("""
                ALTER TABLE outbox_events
                ADD CONSTRAINT reject_payment_initiated_for_test
                CHECK (event_type <> 'payment.initiated')
                """);

        ResponseEntity<String> response = createPayment(
                CUSTOMER_ID,
                "payment-key-rollback",
                UUID.randomUUID(),
                "VNPAY"
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(paymentRepository.count()).isZero();
        assertThat(idempotencyKeyRepository.count()).isZero();
        assertThat(outboxEventRepository.count()).isZero();
    }

    private BookingServiceClient.BookingInfo bookingInfo(UUID bookingId, String status, Instant expiresAt) {
        return new BookingServiceClient.BookingInfo(bookingId, status, BOOKING_AMOUNT, expiresAt);
    }

    private ResponseEntity<String> createPayment(
            String customerId,
            String idempotencyKey,
            UUID bookingId,
            String gateway
    ) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-User-Id", customerId);
        if (idempotencyKey != null) {
            headers.set("Idempotency-Key", idempotencyKey);
        }
        Map<String, Object> body = Map.of("bookingId", bookingId.toString(), "gateway", gateway);
        return restTemplate.exchange(
                "http://localhost:" + port + "/v1/payments",
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                String.class
        );
    }

    private void assertError(ResponseEntity<String> response, HttpStatus status, String errorCode) throws Exception {
        assertThat(response.getStatusCode()).isEqualTo(status);
        assertThat(objectMapper.readTree(response.getBody()).get("error").asText()).isEqualTo(errorCode);
    }
}
