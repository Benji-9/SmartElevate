package com.smartelevate.common.db;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

/** V13 (#211) sobre datos reales: los hashes BCrypt de antes quedan con prefijo {bcrypt} y siguen validando. */
class PasswordHashMigrationTest {

    private static final String URL = "jdbc:h2:mem:v13migration;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE";
    private static final String BCRYPT = "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

    // Todo por la misma conexión: H2 guarda en los CHECK la sesión que los creó y falla si esa sesión ya se cerró.
    private static void migrate(Connection db, String target) {
        Flyway.configure().dataSource(new SingleConnectionDataSource(db, true)).target(target).load().migrate();
    }

    @Test
    void existingBcryptHashesGetThePrefixAndTheColumnFitsArgon2id() throws SQLException {
        try (Connection db = DriverManager.getConnection(URL, "sa", "")) {
            migrate(db, "10");
            db.createStatement().executeUpdate("""
                    INSERT INTO app_user (id, full_name, email, legajo, password_hash, role, declared_user_type,
                                          email_verified, created_at)
                    VALUES (RANDOM_UUID(), 'Ana', 'ana@uade.edu.ar', '1', '%s', 'USER', 'STUDENT', TRUE,
                            CURRENT_TIMESTAMP)
                    """.formatted(BCRYPT));

            migrate(db, "latest");

            ResultSet row = db.createStatement().executeQuery("SELECT password_hash FROM app_user");
            row.next();
            assertThat(row.getString(1)).isEqualTo("{bcrypt}" + BCRYPT);
            // Un hash Argon2id con prefijo ronda los 105 caracteres.
            db.createStatement().executeUpdate("UPDATE app_user SET password_hash = '{argon2}" + "x".repeat(150) + "'");
        }
    }
}
