package com.vietkhampha.paymentservice.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vietkhampha.paymentservice.entity.OutboxEvent;
import com.vietkhampha.paymentservice.entity.Payment;
import com.vietkhampha.paymentservice.entity.PaymentLog;
import com.vietkhampha.paymentservice.repository.OutboxEventRepository;
import com.vietkhampha.paymentservice.repository.PaymentLogRepository;
import com.vietkhampha.paymentservice.repository.PaymentRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.util.UriComponentsBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class VNPayIpnIntegrationTest {

    private static final String IPN_PATH = "/v1/payments/webhooks/vnpay";
    private static final String TMN_CODE = "TEST_MERCHANT";
    private static final String HASH_SECRET = "test-only-vnpay-hash-secret";
    private static final BigDecimal PAYMENT_AMOUNT = new BigDecimal("1250000.00");

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("payment_ipn_test")
            .withUsername("payment_test")
            .withPassword("payment_test_password");

    @DynamicPropertySource
    static void configureTestEnvironment(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.kafka.listener.auto-startup", () -> "false");
        registry.add("spring.kafka.bootstrap-servers", () -> "localhost:1");
        registry.add("app.outbox.poller.enabled", () -> "false");
        registry.add("app.vnpay.tmn-code", () -> TMN_CODE);
        registry.add("app.vnpay.hash-secret", () -> HASH_SECRET);
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
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private PaymentLogRepository paymentLogRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clearDatabase() {
        paymentLogRepository.deleteAll();
        outboxEventRepository.deleteAll();
        paymentRepository.deleteAll();
    }

    @AfterEach
    void removeFailureInjectionConstraint() {
        jdbcTemplate.execute("ALTER TABLE outbox_events DROP CONSTRAINT IF EXISTS reject_payment_succeeded_for_test");
    }

    @Test
    void invalidChecksumIsRejectedBeforeAnyDatabaseMutation() throws Exception {
        Payment payment = createPayment("TXN_BAD_CHECKSUM");
        Map<String, String> params = signedParams(payment.getGatewayTransactionRef(), TMN_CODE,
                "125000000", "00", "00");
        params.put("vnp_SecureHash", "0".repeat(128));

        IpnResponse response = callIpn(params);

        assertThat(response).isEqualTo(new IpnResponse("97", "Invalid Checksum"));
        assertPaymentState(payment.getId(), Payment.Status.INITIATED);
        assertThat(outboxEventsFor(payment.getId())).isEmpty();
        assertThat(paymentLogsFor(payment.getId())).isEmpty();
    }

    @Test
    void unknownTransactionReferenceReturnsOrderNotFound() throws Exception {
        Map<String, String> params = signedParams("TXN_DOES_NOT_EXIST", TMN_CODE,
                "125000000", "00", "00");

        IpnResponse response = callIpn(params);

        assertThat(response).isEqualTo(new IpnResponse("01", "Order not found"));
        assertThat(paymentRepository.count()).isZero();
        assertThat(outboxEventRepository.count()).isZero();
        assertThat(paymentLogRepository.count()).isZero();
    }

    @Test
    void merchantMismatchIsRejectedWithoutChangingPayment() throws Exception {
        Payment payment = createPayment("TXN_WRONG_MERCHANT");
        Map<String, String> params = signedParams(payment.getGatewayTransactionRef(), "OTHER_MERCHANT",
                "125000000", "00", "00");

        IpnResponse response = callIpn(params);

        assertThat(response).isEqualTo(new IpnResponse("99", "Invalid request"));
        assertPaymentState(payment.getId(), Payment.Status.INITIATED);
        assertThat(outboxEventsFor(payment.getId())).isEmpty();
        assertThat(paymentLogsFor(payment.getId())).isEmpty();
    }

    @Test
    void amountMismatchReturnsOfficialInvalidAmountResponse() throws Exception {
        Payment payment = createPayment("TXN_WRONG_AMOUNT");
        Map<String, String> params = signedParams(payment.getGatewayTransactionRef(), TMN_CODE,
                "125000001", "00", "00");

        IpnResponse response = callIpn(params);

        assertThat(response).isEqualTo(new IpnResponse("04", "Invalid amount"));
        assertPaymentState(payment.getId(), Payment.Status.INITIATED);
        assertThat(outboxEventsFor(payment.getId())).isEmpty();
        assertThat(paymentLogsFor(payment.getId())).isEmpty();
    }

    @Test
    void bothVnPayStatusesMustBeSuccessfulBeforePaymentSucceeds() throws Exception {
        Payment payment = createPayment("TXN_SUCCESS");
        Map<String, String> params = signedParams(payment.getGatewayTransactionRef(), TMN_CODE,
                "125000000", "00", "00");

        IpnResponse response = callIpn(params);

        assertThat(response).isEqualTo(new IpnResponse("00", "Confirm Success"));
        assertPaymentState(payment.getId(), Payment.Status.SUCCESS);
        assertSingleEvent(payment.getId(), "payment.succeeded");
        assertSingleIpnLog(payment.getId());
    }

    @ParameterizedTest(name = "responseCode={0}, transactionStatus={1} marks payment failed")
    @CsvSource({"24,00", "00,02"})
    void eitherNonSuccessfulVnPayStatusMarksPaymentFailed(String responseCode,
                                                          String transactionStatus) throws Exception {
        Payment payment = createPayment("TXN_FAILED_" + responseCode + "_" + transactionStatus);
        Map<String, String> params = signedParams(payment.getGatewayTransactionRef(), TMN_CODE,
                "125000000", responseCode, transactionStatus);

        IpnResponse response = callIpn(params);

        assertThat(response).isEqualTo(new IpnResponse("00", "Confirm Success"));
        assertPaymentState(payment.getId(), Payment.Status.FAILED);
        assertSingleEvent(payment.getId(), "payment.failed");
        assertSingleIpnLog(payment.getId());
    }

    @Test
    void replayReturnsAlreadyConfirmedWithoutDuplicatingSideEffects() throws Exception {
        Payment payment = createPayment("TXN_REPLAY");
        Map<String, String> params = signedParams(payment.getGatewayTransactionRef(), TMN_CODE,
                "125000000", "00", "00");

        IpnResponse firstResponse = callIpn(params);
        IpnResponse replayResponse = callIpn(params);

        assertThat(firstResponse).isEqualTo(new IpnResponse("00", "Confirm Success"));
        assertThat(replayResponse).isEqualTo(new IpnResponse("02", "Order already confirmed"));
        assertPaymentState(payment.getId(), Payment.Status.SUCCESS);
        assertSingleEvent(payment.getId(), "payment.succeeded");
        assertSingleIpnLog(payment.getId());
    }

    @Test
    void concurrentSuccessfulCallbacksProduceOneTransitionAndOneOutboxEvent() throws Exception {
        Payment payment = createPayment("TXN_CONCURRENT");
        Map<String, String> params = signedParams(payment.getGatewayTransactionRef(), TMN_CODE,
                "125000000", "00", "00");
        CyclicBarrier startBarrier = new CyclicBarrier(3);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            List<Future<IpnResponse>> futures = new ArrayList<>();
            for (int task = 0; task < 2; task++) {
                futures.add(executor.submit(() -> {
                    startBarrier.await(5, TimeUnit.SECONDS);
                    return callIpn(params);
                }));
            }
            startBarrier.await(5, TimeUnit.SECONDS);

            List<IpnResponse> responses = List.of(
                    futures.get(0).get(10, TimeUnit.SECONDS),
                    futures.get(1).get(10, TimeUnit.SECONDS)
            );

            assertThat(responses).extracting(IpnResponse::responseCode)
                    .containsExactlyInAnyOrder("00", "02");
            assertThat(responses).extracting(IpnResponse::message)
                    .containsExactlyInAnyOrder("Confirm Success", "Order already confirmed");
            assertPaymentState(payment.getId(), Payment.Status.SUCCESS);
            assertSingleEvent(payment.getId(), "payment.succeeded");
            assertSingleIpnLog(payment.getId());
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void outboxFailureRollsBackPaymentLogAndPaymentTransition() throws Exception {
        Payment payment = createPayment("TXN_ROLLBACK");
        jdbcTemplate.execute("""
                ALTER TABLE outbox_events
                ADD CONSTRAINT reject_payment_succeeded_for_test
                CHECK (event_type <> 'payment.succeeded')
                """);
        Map<String, String> params = signedParams(payment.getGatewayTransactionRef(), TMN_CODE,
                "125000000", "00", "00");

        IpnResponse response = callIpn(params);

        assertThat(response).isEqualTo(new IpnResponse("99", "Invalid request"));
        assertPaymentState(payment.getId(), Payment.Status.INITIATED);
        assertThat(outboxEventsFor(payment.getId())).isEmpty();
        assertThat(paymentLogsFor(payment.getId())).isEmpty();
    }

    private Payment createPayment(String transactionRef) {
        Payment payment = new Payment(UUID.randomUUID(), Payment.Gateway.VNPAY, PAYMENT_AMOUNT);
        payment.assignTransactionRef(transactionRef);
        return paymentRepository.saveAndFlush(payment);
    }

    private void assertPaymentState(UUID paymentId, Payment.Status expectedStatus) {
        Payment persisted = paymentRepository.findById(paymentId).orElseThrow();
        assertThat(persisted.getStatus()).isEqualTo(expectedStatus);
        if (expectedStatus == Payment.Status.INITIATED) {
            assertThat(persisted.getCompletedAt()).isNull();
        } else {
            assertThat(persisted.getCompletedAt()).isNotNull();
        }
    }

    private void assertSingleEvent(UUID paymentId, String eventType) {
        assertThat(outboxEventsFor(paymentId))
                .singleElement()
                .extracting(OutboxEvent::getEventType)
                .isEqualTo(eventType);
    }

    private void assertSingleIpnLog(UUID paymentId) {
        assertThat(paymentLogsFor(paymentId))
                .singleElement()
                .extracting(PaymentLog::getEventSource)
                .isEqualTo("WEBHOOK_IPN");
    }

    private List<OutboxEvent> outboxEventsFor(UUID paymentId) {
        return outboxEventRepository.findAll().stream()
                .filter(event -> event.getAggregateId().equals(paymentId))
                .toList();
    }

    private List<PaymentLog> paymentLogsFor(UUID paymentId) {
        return paymentLogRepository.findAll().stream()
                .filter(log -> log.getPaymentId().equals(paymentId))
                .toList();
    }

    private IpnResponse callIpn(Map<String, String> params) throws Exception {
        UriComponentsBuilder uriBuilder = UriComponentsBuilder
                .fromHttpUrl("http://localhost:" + port + IPN_PATH);
        params.forEach(uriBuilder::queryParam);
        URI uri = uriBuilder.build().encode().toUri();

        ResponseEntity<String> response = restTemplate.getForEntity(uri, String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = objectMapper.readTree(response.getBody());
        return new IpnResponse(body.path("RspCode").asText(), body.path("Message").asText());
    }

    private Map<String, String> signedParams(String txnRef, String tmnCode, String amount,
                                              String responseCode, String transactionStatus) throws Exception {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("vnp_TmnCode", tmnCode);
        params.put("vnp_TxnRef", txnRef);
        params.put("vnp_Amount", amount);
        params.put("vnp_ResponseCode", responseCode);
        params.put("vnp_TransactionStatus", transactionStatus);
        params.put("vnp_TransactionNo", "TEST_GATEWAY_TRANSACTION");
        params.put("vnp_PayDate", "20260816140000");
        params.put("vnp_SecureHash", signIndependently(params));
        return params;
    }

    private String signIndependently(Map<String, String> params) throws Exception {
        Map<String, String> sortedVnPayParams = new TreeMap<>();
        params.forEach((key, value) -> {
            if (key.startsWith("vnp_")
                    && !"vnp_SecureHash".equals(key)
                    && !"vnp_SecureHashType".equals(key)) {
                sortedVnPayParams.put(key, value);
            }
        });

        String canonical = sortedVnPayParams.entrySet().stream()
                .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
                .reduce((left, right) -> left + "&" + right)
                .orElse("");
        Mac mac = Mac.getInstance("HmacSHA512");
        mac.init(new SecretKeySpec(HASH_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
        return HexFormat.of().formatHex(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.US_ASCII);
    }

    private record IpnResponse(String responseCode, String message) {
    }
}
