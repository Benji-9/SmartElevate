package com.smartelevate.common.db;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

/** V18 da vencimiento (cierre del cuatrimestre) a las aprobaciones de docente previas, que no vencían. */
class FlywayV18MigrationTest {

    @Test
    void backfillsOnlyApprovedTeachersWithoutExpiration() {
        // Una sola conexión: con H2, los CHECK creados en una sesión ya cerrada fallan al evaluarse.
        SingleConnectionDataSource ds = new SingleConnectionDataSource(
                "jdbc:h2:mem:v18-" + UUID.randomUUID() + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE", true);
        JdbcTemplate jdbc = new JdbcTemplate(ds);
        Flyway.configure().dataSource(ds).target("17").load().migrate();

        String insertUser = "INSERT INTO app_user (id, full_name, email, legajo, password_hash, role,"
                + " declared_user_type, created_at) VALUES (?, ?, ?, ?, 'x', 'USER', 'TEACHER', CURRENT_TIMESTAMP)";
        String insertPriority = "INSERT INTO user_priority (id, user_id, category, status, expires_at, decided_at,"
                + " decided_by) VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP, ?)";
        UUID approved = UUID.randomUUID();
        UUID rejected = UUID.randomUUID();
        UUID mobility = UUID.randomUUID();
        UUID withExpiry = UUID.randomUUID();
        int i = 0;
        for (UUID u : new UUID[] {approved, rejected, mobility, withExpiry}) {
            i++;
            jdbc.update(insertUser, u, "Doc " + i, "doc" + i + "@uade.edu.ar", "100" + i);
        }
        OffsetDateTime set = OffsetDateTime.parse("2026-11-01T03:00:00Z");
        jdbc.update(insertPriority, UUID.randomUUID(), approved, "TEACHER", "APPROVED", null, approved);
        jdbc.update(insertPriority, UUID.randomUUID(), rejected, "TEACHER", "REJECTED", null, rejected);
        jdbc.update(insertPriority, UUID.randomUUID(), mobility, "REDUCED_MOBILITY", "APPROVED", null, mobility);
        jdbc.update(insertPriority, UUID.randomUUID(), withExpiry, "TEACHER", "APPROVED", set, withExpiry);

        Flyway.configure().dataSource(ds).target("18").load().migrate();

        assertThat(expiresAt(jdbc, approved)).isEqualTo(Instant.parse("2027-01-01T03:00:00Z"));
        assertThat(expiresAt(jdbc, rejected)).isNull();
        assertThat(expiresAt(jdbc, mobility)).isNull();
        assertThat(expiresAt(jdbc, withExpiry)).isEqualTo(set.toInstant());
        ds.destroy();
    }

    private static Instant expiresAt(JdbcTemplate jdbc, UUID user) {
        OffsetDateTime at = jdbc.queryForObject("SELECT expires_at FROM user_priority WHERE user_id = ?",
                OffsetDateTime.class, user);
        return at == null ? null : at.toInstant();
    }
}
