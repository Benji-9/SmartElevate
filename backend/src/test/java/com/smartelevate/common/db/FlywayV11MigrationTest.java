package com.smartelevate.common.db;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

/** V11 reemplaza la columna status de reservation: las filas existentes tienen que conservar su estado. */
class FlywayV11MigrationTest {

    @Test
    void keepsTheStatusOfExistingReservationsAndFreesSeatsOfCancelledOnes() {
        // Una sola conexión: con H2, los CHECK creados en una sesión ya cerrada fallan al evaluarse.
        SingleConnectionDataSource ds = new SingleConnectionDataSource(
                "jdbc:h2:mem:v11-" + UUID.randomUUID() + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE", true);
        JdbcTemplate jdbc = new JdbcTemplate(ds);
        Flyway.configure().dataSource(ds).target("10").load().migrate();

        UUID user = UUID.randomUUID();
        jdbc.update("INSERT INTO app_user (id, full_name, email, legajo, password_hash, role, declared_user_type,"
                + " created_at) VALUES (?, 'Ana', 'ana@uade.edu.ar', '1001', 'x', 'USER', 'STUDENT', CURRENT_TIMESTAMP)", user);
        jdbc.update("INSERT INTO departure (id, elevator_code, departure_at, capacity, status)"
                + " VALUES (1, '33', CURRENT_TIMESTAMP, 10, 'SCHEDULED')");
        String insert = "INSERT INTO reservation (id, user_id, departure_id, seat_number, origin_floor,"
                + " destination_floor, status, created_at) VALUES (?, ?, 1, ?, 0, 8, ?, CURRENT_TIMESTAMP)";
        jdbc.update(insert, 1, user, 1, "ACTIVE");
        jdbc.update(insert, 2, user, 2, "CANCELLED");

        Flyway.configure().dataSource(ds).target("11").load().migrate();

        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, status, seat_number FROM reservation ORDER BY id");
        assertThat(rows).extracting(r -> r.get("status")).containsExactly("ACTIVE", "CANCELLED");
        assertThat(rows).extracting(r -> r.get("seat_number")).containsExactly(1, null);
        ds.destroy();
    }
}
