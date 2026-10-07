package com.smartelevate.common.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Las migraciones de Flyway tienen que aplicarse limpias sobre la base de dev (H2 en modo PostgreSQL),
 * la misma que usan los tests. Si una migración tiene un error de SQL o se editó después de aplicarse, falla acá.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class FlywayMigrationsTest {

    @Autowired
    private Flyway flyway;

    @Autowired
    private JdbcTemplate jdbcTemplate;

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
