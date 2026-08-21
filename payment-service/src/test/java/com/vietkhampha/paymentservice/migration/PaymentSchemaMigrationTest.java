package com.vietkhampha.paymentservice.migration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
class PaymentSchemaMigrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("payment_migration_test")
            .withUsername("payment_migration_test")
            .withPassword("payment_migration_test_password");

    @BeforeEach
    void resetSchema() throws Exception {
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            statement.execute("DROP SCHEMA public CASCADE");
            statement.execute("CREATE SCHEMA public");
        }
    }

    @Test
    void cleanDatabase_runsV1AndV2AndIsRepeatable() throws Exception {
        Flyway flyway = flyway();

        MigrateResult first = flyway.migrate();
        MigrateResult second = flyway.migrate();

        assertThat(first.migrationsExecuted).isEqualTo(2);
        assertThat(second.migrationsExecuted).isZero();
        assertThat(queryString("SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank DESC LIMIT 1"))
                .isEqualTo("2");
        assertThat(queryString("SELECT to_regclass('public.payment_idempotency_keys')::text"))
                .isEqualTo("payment_idempotency_keys");
        assertThat(queryString("SELECT to_regclass('public.payments_one_payable_per_booking_idx')::text"))
                .isEqualTo("payments_one_payable_per_booking_idx");
    }

    @Test
    void legacyV1Baseline_migratesWithoutChangingExistingPaymentRows() throws Exception {
        installLegacyV1Schema();
        UUID initiatedPaymentId = insertLegacyPayment(UUID.randomUUID(), "INITIATED", "LEGACY_INITIATED");
        UUID successfulPaymentId = insertLegacyPayment(UUID.randomUUID(), "SUCCESS", "LEGACY_SUCCESS");
        String digestBefore = paymentDigest();

        verifyLegacyV1();
        Flyway flyway = flyway();
        flyway.baseline();
        MigrateResult result = flyway.migrate();

        assertThat(result.migrationsExecuted).isEqualTo(1);
        assertThat(queryLong("SELECT count(*) FROM payments")).isEqualTo(2);
        assertThat(paymentDigest()).isEqualTo(digestBefore);
        assertThat(queryLong("SELECT count(*) FROM payments WHERE id IN ('" + initiatedPaymentId + "', '"
                + successfulPaymentId + "')")).isEqualTo(2);
        assertThat(queryLong("SELECT count(*) FROM flyway_schema_history WHERE type = 'BASELINE' AND version = '1' AND success"))
                .isEqualTo(1);
        assertThat(queryLong("SELECT count(*) FROM flyway_schema_history WHERE version = '2' AND success"))
                .isEqualTo(1);
    }

    @Test
    void legacyPreflight_rejectsDuplicatePayablePaymentsBeforeBaseline() throws Exception {
        installLegacyV1Schema();
        UUID bookingId = UUID.randomUUID();
        insertLegacyPayment(bookingId, "SUCCESS", "DUPLICATE_SUCCESS");
        insertLegacyPayment(bookingId, "INITIATED", "DUPLICATE_INITIATED");
        String digestBefore = paymentDigest();

        assertThatThrownBy(this::verifyLegacyV1)
                .hasStackTraceContaining("multiple INITIATED or SUCCESS payments");

        assertThat(queryString("SELECT to_regclass('public.flyway_schema_history')::text")).isNull();
        assertThat(queryString("SELECT to_regclass('public.payment_idempotency_keys')::text")).isNull();
        assertThat(queryLong("SELECT count(*) FROM payments")).isEqualTo(2);
        assertThat(paymentDigest()).isEqualTo(digestBefore);
    }

    @Test
    void duplicateInitiatedPayments_failV2AndRollbackAllV2Changes() throws Exception {
        installLegacyV1Schema();
        UUID bookingId = UUID.randomUUID();
        insertLegacyPayment(bookingId, "INITIATED", "DUPLICATE_ONE");
        insertLegacyPayment(bookingId, "INITIATED", "DUPLICATE_TWO");
        String digestBefore = paymentDigest();

        Flyway flyway = flyway();
        flyway.baseline();

        assertThatThrownBy(flyway::migrate)
                .isInstanceOf(FlywayException.class)
                .hasMessageContaining("multiple INITIATED or SUCCESS payments");

        assertThat(queryString("SELECT to_regclass('public.payment_idempotency_keys')::text"))
                .isNull();
        assertThat(queryString("SELECT to_regclass('public.payments_one_payable_per_booking_idx')::text"))
                .isNull();
        assertThat(queryLong("SELECT count(*) FROM payments")).isEqualTo(2);
        assertThat(paymentDigest()).isEqualTo(digestBefore);
        assertThat(queryLong("SELECT count(*) FROM flyway_schema_history WHERE version = '2' AND success"))
                .isZero();
    }

    private Flyway flyway() {
        return Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .baselineVersion("1")
                .baselineOnMigrate(false)
                .validateOnMigrate(true)
                .load();
    }

    private void installLegacyV1Schema() throws Exception {
        try (Connection connection = connection()) {
            ScriptUtils.executeSqlScript(
                    connection,
                    new ClassPathResource("db/migration/V1__baseline_payment_schema.sql")
            );
        }
    }

    private void verifyLegacyV1() throws Exception {
        try (Connection connection = connection()) {
            ScriptUtils.executeSqlScript(
                    connection,
                    new EncodedResource(new ClassPathResource("db/preflight/verify_legacy_v1.sql")),
                    false,
                    false,
                    ScriptUtils.DEFAULT_COMMENT_PREFIXES,
                    ScriptUtils.EOF_STATEMENT_SEPARATOR,
                    ScriptUtils.DEFAULT_BLOCK_COMMENT_START_DELIMITER,
                    ScriptUtils.DEFAULT_BLOCK_COMMENT_END_DELIMITER
            );
        }
    }

    private UUID insertLegacyPayment(UUID bookingId, String status, String transactionRef) throws Exception {
        UUID paymentId = UUID.randomUUID();
        try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO payments (
                    id, amount, booking_id, completed_at, created_at, currency,
                    gateway, gateway_transaction_ref, status
                ) VALUES (?, 1250000.00, ?, ?, ?, 'VND', 'VNPAY', ?, ?)
                """)) {
            OffsetDateTime createdAt = OffsetDateTime.now(ZoneOffset.UTC).minusDays(2);
            statement.setObject(1, paymentId);
            statement.setObject(2, bookingId);
            statement.setObject(3, "SUCCESS".equals(status) ? createdAt.plusMinutes(5) : null);
            statement.setObject(4, createdAt);
            statement.setString(5, transactionRef);
            statement.setString(6, status);
            statement.executeUpdate();
        }
        return paymentId;
    }

    private String paymentDigest() throws Exception {
        return queryString("""
                SELECT md5(COALESCE(string_agg(
                    id::text || '|' || booking_id::text || '|' || amount::text || '|' ||
                    gateway || '|' || gateway_transaction_ref || '|' || status || '|' ||
                    created_at::text || '|' || COALESCE(completed_at::text, '<null>'),
                    E'\\n' ORDER BY id
                ), ''))
                FROM payments
                """);
    }

    private long queryLong(String sql) throws Exception {
        try (Connection connection = connection(); Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(sql)) {
            result.next();
            return result.getLong(1);
        }
    }

    private String queryString(String sql) throws Exception {
        try (Connection connection = connection(); Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(sql)) {
            result.next();
            return result.getString(1);
        }
    }

    private Connection connection() throws Exception {
        return DriverManager.getConnection(
                POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword()
        );
    }
}
