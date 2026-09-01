package com.vietkhampha.userservice.migration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class UserProfileSchemaMigrationTest {

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
    void cleanDatabaseRunsAllMigrationsAndRestartIsRepeatable() throws Exception {
        Flyway flyway = flyway(false);

        MigrateResult first = flyway.migrate();
        MigrateResult second = flyway.migrate();

        assertThat(first.migrationsExecuted).isEqualTo(2);
        assertThat(second.migrationsExecuted).isZero();
        assertThat(queryString("SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank DESC LIMIT 1"))
                .isEqualTo("2");
        assertThat(queryString("SELECT character_maximum_length::text FROM information_schema.columns WHERE table_name='user_profiles' AND column_name='avatar_url'"))
                .isEqualTo("2048");
        assertThat(queryString("SELECT to_regclass('public.user_preference_tags')::text"))
                .isEqualTo("user_preference_tags");
    }

    @Test
    void compatibleLegacySchemaCanBeBaselinedAndDataIsPreserved() throws Exception {
        UUID profileId = UUID.randomUUID();
        UUID authUserId = UUID.randomUUID();
        try (Connection connection = connection()) {
            ScriptUtils.executeSqlScript(
                    connection,
                    new ClassPathResource("db/migration/V1__baseline_user_profile_schema.sql")
            );
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("""
                        INSERT INTO user_profiles(id, auth_user_id, phone, created_at, updated_at)
                        VALUES ('%s', '%s', '0912345678', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                        """.formatted(profileId, authUserId));
            }
        }

        MigrateResult result = flyway(true).migrate();

        assertThat(result.migrationsExecuted).isEqualTo(1);
        assertThat(queryString("SELECT phone FROM user_profiles WHERE id='" + profileId + "'"))
                .isEqualTo("0912345678");
        assertThat(queryString("SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank DESC LIMIT 1"))
                .isEqualTo("2");
    }

    private Flyway flyway(boolean baselineOnMigrate) {
        return Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .baselineOnMigrate(baselineOnMigrate)
                .baselineVersion("1")
                .validateOnMigrate(true)
                .load();
    }

    private String queryString(String sql) throws Exception {
        try (Connection connection = connection(); Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            resultSet.next();
            return resultSet.getString(1);
        }
    }

    private Connection connection() throws Exception {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }
}
