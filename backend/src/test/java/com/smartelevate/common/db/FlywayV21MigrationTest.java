package com.smartelevate.common.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

/** V21 suma el destino "ascensor" a la auditoría: las filas viejas sobreviven y el destino sigue siendo uno solo. */
class FlywayV21MigrationTest {

    private static final String INSERT_USER_AUDIT = "INSERT INTO priority_audit (id, actor_id, target_user_id,"
            + " action, created_at) VALUES (?, ?, ?, 'ROLE_CHANGED', CURRENT_TIMESTAMP)";
    private static final String INSERT_KIOSK_AUDIT = "INSERT INTO priority_audit (id, actor_id, target_kiosk_id,"
            + " action, created_at) VALUES (?, ?, ?, 'KIOSK_CREATED', CURRENT_TIMESTAMP)";

    @Test
    void keepsUserAndKioskAuditRowsAndRequiresExactlyOneTarget() {
        // Una sola conexión: con H2, los CHECK creados en una sesión ya cerrada fallan al evaluarse.
        SingleConnectionDataSource ds = new SingleConnectionDataSource(
                "jdbc:h2:mem:v21-" + UUID.randomUUID() + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE", true);
        JdbcTemplate jdbc = new JdbcTemplate(ds);
        Flyway.configure().dataSource(ds).target("20").load().migrate();

        UUID admin = UUID.randomUUID();
        UUID target = UUID.randomUUID();
        UUID kiosk = UUID.randomUUID();
        String insertUser = "INSERT INTO app_user (id, full_name, email, legajo, password_hash, role,"
                + " declared_user_type, created_at) VALUES (?, ?, ?, ?, 'x', 'USER', 'STUDENT', CURRENT_TIMESTAMP)";
        jdbc.update(insertUser, admin, "Admin", "admin@uade.edu.ar", "1");
        jdbc.update(insertUser, target, "Target", "target@uade.edu.ar", "2");
        jdbc.update("INSERT INTO kiosk (id, elevator_code, secret_hash, simulated, created_at)"
                + " VALUES (?, '33', 'hash', false, CURRENT_TIMESTAMP)", kiosk);
        UUID userRow = UUID.randomUUID();
        UUID kioskRow = UUID.randomUUID();
        jdbc.update(INSERT_USER_AUDIT, userRow, admin, target);
        jdbc.update(INSERT_KIOSK_AUDIT, kioskRow, admin, kiosk);

        Flyway.configure().dataSource(ds).target("21").load().migrate();

        assertThat(jdbc.queryForObject("SELECT target_user_id FROM priority_audit WHERE id = ?", UUID.class, userRow))
                .isEqualTo(target);
        assertThat(jdbc.queryForObject("SELECT target_kiosk_id FROM priority_audit WHERE id = ?", UUID.class,
                kioskRow)).isEqualTo(kiosk);
        assertThat(jdbc.queryForList("SELECT id FROM priority_audit WHERE target_elevator_code IS NULL")).hasSize(2);

        // Destino ascensor: vale solo; ninguno o dos destinos no.
        jdbc.update("INSERT INTO priority_audit (id, actor_id, target_elevator_code, action, created_at)"
                + " VALUES (?, ?, '33', 'ELEVATOR_OUT_OF_SERVICE', CURRENT_TIMESTAMP)", UUID.randomUUID(), admin);
        rejected(() -> jdbc.update("INSERT INTO priority_audit (id, actor_id, action, created_at)"
                + " VALUES (?, ?, 'ROLE_CHANGED', CURRENT_TIMESTAMP)", UUID.randomUUID(), admin), "cero destinos");
        rejected(() -> jdbc.update("INSERT INTO priority_audit (id, actor_id, target_user_id, target_kiosk_id,"
                + " action, created_at) VALUES (?, ?, ?, ?, 'ROLE_CHANGED', CURRENT_TIMESTAMP)",
                UUID.randomUUID(), admin, target, kiosk), "usuario y kiosco");
        rejected(() -> jdbc.update("INSERT INTO priority_audit (id, actor_id, target_user_id,"
                + " target_elevator_code, action, created_at) VALUES (?, ?, ?, '33', 'ROLE_CHANGED',"
                + " CURRENT_TIMESTAMP)", UUID.randomUUID(), admin, target), "usuario y ascensor");
        rejected(() -> jdbc.update("INSERT INTO priority_audit (id, actor_id, target_kiosk_id,"
                + " target_elevator_code, action, created_at) VALUES (?, ?, ?, '33', 'KIOSK_CREATED',"
                + " CURRENT_TIMESTAMP)", UUID.randomUUID(), admin, kiosk), "kiosco y ascensor");
        ds.destroy();
    }

    private static void rejected(org.assertj.core.api.ThrowableAssert.ThrowingCallable insert, String why) {
        assertThatThrownBy(insert).as(why).isInstanceOf(DataIntegrityViolationException.class)
                .satisfies(e -> assertThat(e.getMessage().toLowerCase()).contains("ck_priority_audit_target"));
    }
}
