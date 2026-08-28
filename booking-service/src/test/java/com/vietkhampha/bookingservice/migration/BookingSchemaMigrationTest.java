package com.vietkhampha.bookingservice.migration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ClassPathResource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
class BookingSchemaMigrationTest {

    private static final String MIGRATION_LOCATION = "classpath:db/migration";
    private static final String TEST_BOOKINGS_CHECK_NAME = "test_bookings_status_check";
    private static final List<String> V1_BOOKING_STATUSES = List.of(
            "CANCELLED",
            "COMPLETED",
            "CONFIRMED",
            "EXPIRED",
            "PAYMENT_FAILED",
            "PENDING"
    );

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @BeforeEach
    void resetDatabase() throws SQLException {
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            statement.execute("DROP SCHEMA public CASCADE");
            statement.execute("CREATE SCHEMA public");
        }
    }

    @Test
    void cleanDatabase_runsAllMigrations_andRestartDoesNotReapplyMigrations() throws SQLException {
        Flyway flyway = flyway(MIGRATION_LOCATION);

        MigrateResult firstStart = flyway.migrate();

        assertEquals(6, firstStart.migrationsExecuted);
        assertEquals(6, queryForInt("SELECT count(*) FROM flyway_schema_history WHERE success"));
        assertTrue(columnExists("idempotency_keys", "customer_id"));
        assertTrue(columnExists("idempotency_keys", "response_snapshot"));
        assertTrue(bookingStatusConstraint().contains("PAYMENT_REVIEW_REQUIRED"));
        assertTrue(tableExists("processed_payment_events"));
        assertTrue(columnExists("tour_slots", "end_date"));
        assertTrue(columnExists("tour_slots", "guide_id"));
        assertTrue(columnExists("bookings", "tour_id"));
        assertTrue(columnExists("bookings", "commercial_snapshot"));
        assertTrue(columnExists("booking_participants", "participant_type"));
        assertEquals(List.of("event_id"), primaryKeyColumns("processed_payment_events"));
        assertEquals(4, queryForInt("""
                SELECT count(*)
                FROM pg_constraint constraint_definition
                JOIN pg_class source_table ON source_table.oid = constraint_definition.conrelid
                JOIN pg_namespace source_schema ON source_schema.oid = source_table.relnamespace
                WHERE source_schema.nspname = 'public'
                  AND source_table.relname = 'processed_payment_events'
                  AND constraint_definition.convalidated
                  AND constraint_definition.conname IN (
                      'processed_payment_events_pkey',
                      'processed_payment_events_payment_key',
                      'processed_payment_events_booking_fkey',
                      'processed_payment_events_type_check'
                  )
                """));
        assertEquals(1, queryForInt("""
                SELECT count(*)
                FROM pg_indexes
                WHERE schemaname = 'public'
                  AND tablename = 'processed_payment_events'
                  AND indexname = 'idx_processed_payment_events_booking_id'
                """));

        MigrateResult secondStart = flyway(MIGRATION_LOCATION).migrate();

        assertEquals(0, secondStart.migrationsExecuted);
        assertEquals(6, queryForInt("SELECT count(*) FROM flyway_schema_history WHERE success"));
    }

    @Test
    void legacyDatabase_preservesTwentyExpiredRecords_andDoesNotDependOnConstraintName() throws Exception {
        executeResource("db/migration/V1__baseline_booking_schema.sql");
        executeSql("ALTER TABLE bookings RENAME CONSTRAINT bookings_status_check TO legacy_status_check_42");
        List<LegacyRecord> legacyRecords = insertLegacyRecords(20);

        executeResource("db/preflight/verify_legacy_v1.sql");
        migrateWithOneTimeBaselineOverride();

        assertEquals(20, queryForInt("SELECT count(*) FROM idempotency_keys"));
        assertEquals(20, queryForInt("""
                SELECT count(*)
                FROM idempotency_keys
                WHERE record_state = 'LEGACY_EXPIRED'
                  AND request_hash IS NULL
                  AND hash_version IS NULL
                  AND response_snapshot IS NULL
                  AND booking_id IS NOT NULL
                """));
        assertEquals(0, queryForInt("""
                SELECT count(*)
                FROM idempotency_keys legacy_key
                JOIN bookings booking ON booking.id = legacy_key.booking_id
                WHERE legacy_key.customer_id <> booking.customer_id
                """));
        assertEquals(legacyRecords, readLegacyRecords());
        assertTrue(bookingStatusConstraint().contains("PAYMENT_REVIEW_REQUIRED"));
        assertEquals(List.of("customer_id", "key"), primaryKeyColumns("idempotency_keys"));
        assertEquals("BASELINE", queryForString("""
                SELECT type FROM flyway_schema_history WHERE version = '1'
                """));
        assertEquals(6, queryForInt("SELECT count(*) FROM flyway_schema_history WHERE success"));
    }

    @Test
    void legacyPreflightRejectsSchemaThatDoesNotMatchV1() throws Exception {
        executeResource("db/migration/V1__baseline_booking_schema.sql");
        executeSql("ALTER TABLE bookings ADD COLUMN unexpected_column text");

        assertThrows(SQLException.class, () -> executeResource("db/preflight/verify_legacy_v1.sql"));

        assertFalse(tableExists("flyway_schema_history"));
    }

    @Test
    void legacyPreflight_acceptsEquivalentStatusCheckWithDifferentRenderedDefinition_andMigrationContinues()
            throws Exception {
        executeResource("db/migration/V1__baseline_booking_schema.sql");
        List<LegacyRecord> legacyRecords = insertLegacyRecords(1);
        String originalDefinition = checkConstraintMetadata("bookings_status_check").definition();

        replaceBookingsStatusCheck("""
                status IN (
                    'COMPLETED',
                    'CANCELLED',
                    'PAYMENT_FAILED',
                    'EXPIRED',
                    'CONFIRMED',
                    'PENDING'
                )
                """, false);

        CheckConstraintMetadata equivalentCheck = checkConstraintMetadata(TEST_BOOKINGS_CHECK_NAME);
        assertNotEquals(
                originalDefinition,
                equivalentCheck.definition(),
                "The fixture must have a PostgreSQL-rendered definition different from V1"
        );
        assertEquals(List.of("status"), equivalentCheck.sourceColumns());
        assertEquals(V1_BOOKING_STATUSES, equivalentCheck.acceptedValues());
        assertTrue(equivalentCheck.validated());

        executeResource("db/preflight/verify_legacy_v1.sql");
        migrateWithOneTimeBaselineOverride();

        assertEquals(legacyRecords, readLegacyRecords());
        assertEquals(1, queryForInt("""
                SELECT count(*)
                FROM idempotency_keys
                WHERE record_state = 'LEGACY_EXPIRED'
                  AND request_hash IS NULL
                  AND hash_version IS NULL
                  AND response_snapshot IS NULL
                """));
        assertEquals(List.of("customer_id", "key"), primaryKeyColumns("idempotency_keys"));
        assertEquals(6, queryForInt("SELECT count(*) FROM flyway_schema_history WHERE success"));
    }

    @Test
    void legacyPreflight_rejectsStatusCheckMissingOneState() throws Exception {
        executeResource("db/migration/V1__baseline_booking_schema.sql");
        List<LegacyRecord> legacyRecords = insertLegacyRecords(1);
        replaceBookingsStatusCheck("""
                status IN (
                    'PENDING',
                    'CONFIRMED',
                    'EXPIRED',
                    'PAYMENT_FAILED',
                    'CANCELLED'
                )
                """, false);

        CheckConstraintMetadata missingStateCheck = checkConstraintMetadata(TEST_BOOKINGS_CHECK_NAME);
        assertEquals(List.of("status"), missingStateCheck.sourceColumns());
        assertEquals(
                List.of("CANCELLED", "CONFIRMED", "EXPIRED", "PAYMENT_FAILED", "PENDING"),
                missingStateCheck.acceptedValues()
        );
        assertTrue(missingStateCheck.validated());

        assertLegacyPreflightRejectsWithoutMigration(legacyRecords, missingStateCheck);
    }

    @Test
    void legacyPreflight_rejectsStatusCheckWithOneExtraState() throws Exception {
        executeResource("db/migration/V1__baseline_booking_schema.sql");
        List<LegacyRecord> legacyRecords = insertLegacyRecords(1);
        replaceBookingsStatusCheck("""
                status IN (
                    'PENDING',
                    'CONFIRMED',
                    'EXPIRED',
                    'PAYMENT_FAILED',
                    'CANCELLED',
                    'COMPLETED',
                    'ARCHIVED'
                )
                """, false);

        CheckConstraintMetadata extraStateCheck = checkConstraintMetadata(TEST_BOOKINGS_CHECK_NAME);
        assertEquals(List.of("status"), extraStateCheck.sourceColumns());
        assertEquals(
                List.of("ARCHIVED", "CANCELLED", "COMPLETED", "CONFIRMED", "EXPIRED", "PAYMENT_FAILED", "PENDING"),
                extraStateCheck.acceptedValues()
        );
        assertTrue(extraStateCheck.validated());

        assertLegacyPreflightRejectsWithoutMigration(legacyRecords, extraStateCheck);
    }

    @Test
    void legacyPreflight_rejectsEquivalentStateSetBoundToWrongColumn() throws Exception {
        executeResource("db/migration/V1__baseline_booking_schema.sql");
        List<LegacyRecord> legacyRecords = insertLegacyRecords(1);
        replaceBookingsStatusCheck("""
                generated_itinerary_id IN (
                    'PENDING',
                    'CONFIRMED',
                    'EXPIRED',
                    'PAYMENT_FAILED',
                    'CANCELLED',
                    'COMPLETED'
                )
                """, false);

        CheckConstraintMetadata wrongColumnCheck = checkConstraintMetadata(TEST_BOOKINGS_CHECK_NAME);
        assertEquals(List.of("generated_itinerary_id"), wrongColumnCheck.sourceColumns());
        assertEquals(V1_BOOKING_STATUSES, wrongColumnCheck.acceptedValues());
        assertTrue(wrongColumnCheck.validated());

        assertLegacyPreflightRejectsWithoutMigration(legacyRecords, wrongColumnCheck);
    }

    @Test
    void legacyPreflight_rejectsNotValidatedStatusCheck() throws Exception {
        executeResource("db/migration/V1__baseline_booking_schema.sql");
        List<LegacyRecord> legacyRecords = insertLegacyRecords(1);
        replaceBookingsStatusCheck("""
                status IN (
                    'PENDING',
                    'CONFIRMED',
                    'EXPIRED',
                    'PAYMENT_FAILED',
                    'CANCELLED',
                    'COMPLETED'
                )
                """, true);

        CheckConstraintMetadata notValidatedCheck = checkConstraintMetadata(TEST_BOOKINGS_CHECK_NAME);
        assertEquals(List.of("status"), notValidatedCheck.sourceColumns());
        assertEquals(V1_BOOKING_STATUSES, notValidatedCheck.acceptedValues());
        assertFalse(notValidatedCheck.validated());
        assertEquals(0, queryForInt("""
                SELECT count(*)
                FROM bookings
                WHERE status NOT IN (
                    'PENDING', 'CONFIRMED', 'EXPIRED',
                    'PAYMENT_FAILED', 'CANCELLED', 'COMPLETED'
                )
                """));

        assertLegacyPreflightRejectsWithoutMigration(legacyRecords, notValidatedCheck);
    }

    @Test
    void failingV3_rollsBackAllSchemaAndDataChanges(@TempDir Path temporaryDirectory) throws Exception {
        executeResource("db/migration/V1__baseline_booking_schema.sql");
        List<LegacyRecord> legacyRecords = insertLegacyRecords(20);
        baselineAtVersionOne();
        flyway(MIGRATION_LOCATION, MigrationVersion.fromVersion("2")).migrate();

        copyMigration("V1__baseline_booking_schema.sql", temporaryDirectory, false);
        copyMigration("V2__align_booking_status_constraint.sql", temporaryDirectory, false);
        copyMigration("V3__harden_booking_idempotency.sql", temporaryDirectory, true);

        String failureLocation = "filesystem:" + temporaryDirectory.toAbsolutePath().toString().replace('\\', '/');
        assertThrows(FlywayException.class, () -> flyway(failureLocation).migrate());

        assertFalse(columnExists("idempotency_keys", "customer_id"));
        assertEquals(List.of("key"), primaryKeyColumns("idempotency_keys"));
        assertEquals(legacyRecords, readLegacyRecords());
        assertEquals(0, queryForInt("SELECT count(*) FROM flyway_schema_history WHERE version = '3'"));
    }

    private Flyway flyway(String location) {
        return Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations(location)
                .baselineOnMigrate(false)
                .load();
    }

    private Flyway flyway(String location, MigrationVersion target) {
        return Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations(location)
                .baselineOnMigrate(false)
                .target(target)
                .load();
    }

    private void baselineAtVersionOne() {
        Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations(MIGRATION_LOCATION)
                .baselineVersion(MigrationVersion.fromVersion("1"))
                .baselineDescription("Existing Booking Service schema")
                .baselineOnMigrate(false)
                .load()
                .baseline();
    }

    private void migrateWithOneTimeBaselineOverride() {
        Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations(MIGRATION_LOCATION)
                .baselineVersion(MigrationVersion.fromVersion("1"))
                .baselineDescription("Existing Booking Service schema")
                .baselineOnMigrate(true)
                .load()
                .migrate();
    }

    private void copyMigration(String fileName, Path targetDirectory, boolean appendFailure) throws IOException {
        String contents = readResource("db/migration/" + fileName);
        if (appendFailure) {
            contents += System.lineSeparator()
                    + "ALTER TABLE public.idempotency_keys ADD COLUMN should_rollback integer;"
                    + System.lineSeparator()
                    + "SELECT 1 / 0;"
                    + System.lineSeparator();
        }
        Files.writeString(targetDirectory.resolve(fileName), contents, StandardCharsets.UTF_8);
    }

    private void replaceBookingsStatusCheck(String predicate, boolean notValid) throws SQLException {
        executeSql("ALTER TABLE public.bookings DROP CONSTRAINT bookings_status_check");
        executeSql("""
                ALTER TABLE public.bookings
                ADD CONSTRAINT %s CHECK (%s) %s
                """.formatted(
                TEST_BOOKINGS_CHECK_NAME,
                predicate,
                notValid ? "NOT VALID" : ""
        ));
    }

    private CheckConstraintMetadata checkConstraintMetadata(String constraintName) throws SQLException {
        String sql = """
                SELECT
                    pg_get_constraintdef(constraint_definition.oid, true) AS definition,
                    constraint_definition.convalidated AS validated,
                    ARRAY(
                        SELECT attribute.attname::text
                        FROM unnest(constraint_definition.conkey) WITH ORDINALITY
                            key_column(attribute_number, position)
                        JOIN pg_attribute attribute
                          ON attribute.attrelid = constraint_definition.conrelid
                         AND attribute.attnum = key_column.attribute_number
                         AND NOT attribute.attisdropped
                        ORDER BY key_column.position
                    ) AS source_columns,
                    ARRAY(
                        SELECT DISTINCT (matched_value)[1]
                        FROM regexp_matches(
                            pg_get_expr(
                                constraint_definition.conbin,
                                constraint_definition.conrelid,
                                true
                            ),
                            '''([^'']+)''',
                            'g'
                        ) AS matched_values(matched_value)
                        ORDER BY (matched_value)[1]
                    ) AS accepted_values
                FROM pg_constraint constraint_definition
                WHERE constraint_definition.conrelid = 'public.bookings'::regclass
                  AND constraint_definition.contype = 'c'
                  AND constraint_definition.conname = ?
                """;
        try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, constraintName);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertTrue(resultSet.next(), "Expected CHECK constraint " + constraintName);
                return new CheckConstraintMetadata(
                        resultSet.getString("definition"),
                        readTextArray(resultSet, "source_columns"),
                        readTextArray(resultSet, "accepted_values"),
                        resultSet.getBoolean("validated")
                );
            }
        }
    }

    private List<String> readTextArray(ResultSet resultSet, String columnName) throws SQLException {
        return List.of((String[]) resultSet.getArray(columnName).getArray());
    }

    private void assertLegacyPreflightRejectsWithoutMigration(
            List<LegacyRecord> legacyRecords,
            CheckConstraintMetadata fixtureMetadata
    ) throws Exception {
        SQLException exception = assertThrows(
                SQLException.class,
                () -> executeResource("db/preflight/verify_legacy_v1.sql")
        );

        assertTrue(
                exception.getMessage().contains("Legacy schema is not compatible with V1"),
                () -> "Expected the preflight schema incompatibility error but got: " + exception.getMessage()
        );
        assertFalse(tableExists("flyway_schema_history"));
        assertFalse(columnExists("idempotency_keys", "customer_id"));
        assertEquals(List.of("key"), primaryKeyColumns("idempotency_keys"));
        assertEquals(legacyRecords, readLegacyRecords());
        assertEquals(fixtureMetadata, checkConstraintMetadata(TEST_BOOKINGS_CHECK_NAME));
    }

    private List<LegacyRecord> insertLegacyRecords(int count) throws SQLException {
        Instant createdAt = Instant.now().minus(72, ChronoUnit.HOURS).truncatedTo(ChronoUnit.MICROS);
        Instant expiresAt = createdAt.plus(24, ChronoUnit.HOURS);
        List<LegacyRecord> records = new ArrayList<>();

        try (Connection connection = connection();
             PreparedStatement slotStatement = connection.prepareStatement("""
                     INSERT INTO tour_slots (
                         id, booked_count, departure_date, max_capacity, status, tour_id, version
                     ) VALUES (?, 1, ?, 10, 'OPEN', ?, 0)
                     """);
             PreparedStatement bookingStatement = connection.prepareStatement("""
                     INSERT INTO bookings (
                         id, created_at, customer_id, generated_itinerary_id, hold_expires_at,
                         participant_count, status, total_amount, tour_slot_id, updated_at
                     ) VALUES (?, ?, ?, NULL, ?, 1, 'PENDING', 1000000.00, ?, ?)
                     """);
             PreparedStatement keyStatement = connection.prepareStatement("""
                     INSERT INTO idempotency_keys (key, booking_id, created_at, expires_at)
                     VALUES (?, ?, ?, ?)
                     """)) {
            for (int index = 0; index < count; index++) {
                UUID bookingId = UUID.randomUUID();
                UUID customerId = UUID.randomUUID();
                UUID tourSlotId = UUID.randomUUID();
                String key = "legacy-key-" + index;

                slotStatement.setObject(1, tourSlotId);
                slotStatement.setObject(2, java.time.LocalDate.now().plusDays(30 + index));
                slotStatement.setString(3, "legacy-tour-" + index);
                slotStatement.addBatch();

                bookingStatement.setObject(1, bookingId);
                bookingStatement.setObject(2, createdAt.atOffset(ZoneOffset.UTC));
                bookingStatement.setObject(3, customerId);
                bookingStatement.setObject(4, createdAt.plus(15, ChronoUnit.MINUTES).atOffset(ZoneOffset.UTC));
                bookingStatement.setObject(5, tourSlotId);
                bookingStatement.setObject(6, createdAt.atOffset(ZoneOffset.UTC));
                bookingStatement.addBatch();

                keyStatement.setString(1, key);
                keyStatement.setObject(2, bookingId);
                keyStatement.setObject(3, createdAt.atOffset(ZoneOffset.UTC));
                keyStatement.setObject(4, expiresAt.atOffset(ZoneOffset.UTC));
                keyStatement.addBatch();

                records.add(new LegacyRecord(key, bookingId, createdAt, expiresAt));
            }
            slotStatement.executeBatch();
            bookingStatement.executeBatch();
            keyStatement.executeBatch();
        }
        records.sort(java.util.Comparator.comparing(LegacyRecord::key));
        return records;
    }

    private List<LegacyRecord> readLegacyRecords() throws SQLException {
        List<LegacyRecord> records = new ArrayList<>();
        String sql = """
                SELECT key, booking_id, created_at, expires_at
                FROM idempotency_keys
                ORDER BY key
                """;
        try (Connection connection = connection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            while (resultSet.next()) {
                records.add(new LegacyRecord(
                        resultSet.getString("key"),
                        resultSet.getObject("booking_id", UUID.class),
                        resultSet.getObject("created_at", java.time.OffsetDateTime.class).toInstant(),
                        resultSet.getObject("expires_at", java.time.OffsetDateTime.class).toInstant()
                ));
            }
        }
        return records;
    }

    private List<String> primaryKeyColumns(String tableName) throws SQLException {
        String sql = """
                SELECT attribute.attname
                FROM pg_constraint constraint_definition
                CROSS JOIN LATERAL unnest(constraint_definition.conkey)
                    WITH ORDINALITY key_column(attribute_number, position)
                JOIN pg_attribute attribute
                  ON attribute.attrelid = constraint_definition.conrelid
                 AND attribute.attnum = key_column.attribute_number
                WHERE constraint_definition.conrelid = ?::regclass
                  AND constraint_definition.contype = 'p'
                ORDER BY key_column.position
                """;
        List<String> columns = new ArrayList<>();
        try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, "public." + tableName);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    columns.add(resultSet.getString(1));
                }
            }
        }
        return columns;
    }

    private String bookingStatusConstraint() throws SQLException {
        String sql = """
                SELECT pg_get_constraintdef(constraint_definition.oid, true)
                FROM pg_constraint constraint_definition
                JOIN pg_attribute attribute
                  ON attribute.attrelid = constraint_definition.conrelid
                 AND attribute.attname = 'status'
                 AND attribute.attnum = ANY (constraint_definition.conkey)
                WHERE constraint_definition.conrelid = 'public.bookings'::regclass
                  AND constraint_definition.contype = 'c'
                """;
        try (Connection connection = connection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            assertTrue(resultSet.next());
            return resultSet.getString(1);
        }
    }

    private boolean columnExists(String tableName, String columnName) throws SQLException {
        String sql = """
                SELECT EXISTS (
                    SELECT 1
                    FROM information_schema.columns
                    WHERE table_schema = 'public'
                      AND table_name = ?
                      AND column_name = ?
                )
                """;
        try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, tableName);
            statement.setString(2, columnName);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getBoolean(1);
            }
        }
    }

    private boolean tableExists(String tableName) throws SQLException {
        String sql = """
                SELECT EXISTS (
                    SELECT 1
                    FROM information_schema.tables
                    WHERE table_schema = 'public'
                      AND table_name = ?
                )
                """;
        try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, tableName);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getBoolean(1);
            }
        }
    }

    private int queryForInt(String sql) throws SQLException {
        try (Connection connection = connection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            resultSet.next();
            return resultSet.getInt(1);
        }
    }

    private String queryForString(String sql) throws SQLException {
        try (Connection connection = connection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            resultSet.next();
            return resultSet.getString(1);
        }
    }

    private void executeResource(String path) throws IOException, SQLException {
        executeSql(readResource(path));
    }

    private String readResource(String path) throws IOException {
        return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
    }

    private void executeSql(String sql) throws SQLException {
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private Connection connection() throws SQLException {
        return java.sql.DriverManager.getConnection(
                postgres.getJdbcUrl(),
                postgres.getUsername(),
                postgres.getPassword()
        );
    }

    private record LegacyRecord(String key, UUID bookingId, Instant createdAt, Instant expiresAt) {
    }

    private record CheckConstraintMetadata(
            String definition,
            List<String> sourceColumns,
            List<String> acceptedValues,
            boolean validated
    ) {
    }
}
