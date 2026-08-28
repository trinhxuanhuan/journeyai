package com.vietkhampha.notificationservice.migration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class NotificationSchemaMigrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @BeforeEach
    void resetSchema() throws Exception {
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            statement.execute("DROP SCHEMA public CASCADE");
            statement.execute("CREATE SCHEMA public");
        }
    }

    @Test
    void cleanDatabaseRunsMigrationAndRestartIsRepeatable() throws Exception {
        Flyway flyway = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .baselineOnMigrate(false)
                .validateOnMigrate(true)
                .load();

        MigrateResult first = flyway.migrate();
        MigrateResult second = flyway.migrate();

        assertThat(first.migrationsExecuted).isEqualTo(1);
        assertThat(second.migrationsExecuted).isZero();
        assertThat(queryString("SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank DESC LIMIT 1"))
                .isEqualTo("1");
        assertThat(queryString("SELECT to_regclass('public.notification_recipients')::text"))
                .isEqualTo("notification_recipients");
        assertThat(queryString("SELECT to_regclass('public.notifications')::text"))
                .isEqualTo("notifications");
        assertThat(queryString("SELECT to_regclass('public.notification_event_inbox')::text"))
                .isEqualTo("notification_event_inbox");
        assertThat(queryString("SELECT to_regclass('public.notification_email_deliveries')::text"))
                .isEqualTo("notification_email_deliveries");
    }

    private String queryString(String sql) throws Exception {
        try (Connection connection = connection(); Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(sql)) {
            result.next();
            return result.getString(1);
        }
    }

    private Connection connection() throws Exception {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }
}
