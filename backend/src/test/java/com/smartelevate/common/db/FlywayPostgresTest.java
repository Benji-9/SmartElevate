package com.smartelevate.common.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Las migraciones de Flyway tienen que aplicarse limpias sobre Postgres real, no solo sobre H2 (ADR 0004).
 * Sin Docker se saltea; en CI corre siempre.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(PostgresTestcontainersConfig.class)
@Testcontainers(disabledWithoutDocker = true)
class FlywayPostgresTest {

    @Autowired
    private Flyway flyway;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void runsAgainstPostgres() {
        assertThat(jdbcTemplate.queryForObject("SELECT version()", String.class)).startsWith("PostgreSQL 18");
    }

    @Test
    void noMigrationIsPendingOrInvalid() {
        assertThat(flyway.info().pending()).isEmpty();
        assertThatNoException().isThrownBy(flyway::validate);
    }

    @Test
    void flywayKeepsItsHistoryInTheDatabase() {
        assertThatNoException().isThrownBy(
                () -> jdbcTemplate.queryForObject("SELECT COUNT(*) FROM flyway_schema_history", Integer.class));
    }
}
