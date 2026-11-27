package com.smartelevate.user.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smartelevate.user.model.DeclaredUserType;
import com.smartelevate.user.model.User;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/** Las garantías que da la propia base (V5__users.sql), aunque falle o se saltee el código de la app. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class UserRepositoryTest {

    @Autowired
    private UserRepository users;

    @Autowired
    private JdbcTemplate jdbc;

    private static User user(String email, String legajo) {
        return new User("Ana Pérez", email, legajo, "$2a$10$hashDeEjemplo", DeclaredUserType.STUDENT,
                Instant.parse("2026-10-13T15:00:00Z"));
    }

    private void insertByHand(String role) {
        // Sin pasar por la entidad: solo las columnas obligatorias, para ver qué completa la base sola.
        jdbc.update("""
                INSERT INTO app_user (id, full_name, email, legajo, password_hash, declared_user_type, created_at)
                VALUES (?, 'Ana Pérez', 'ana@uade.edu.ar', '1111111', 'x', 'STUDENT', ?)""",
                UUID.randomUUID(), OffsetDateTime.now(ZoneOffset.UTC));
        if (role != null) {
            jdbc.update("UPDATE app_user SET role = ? WHERE email = 'ana@uade.edu.ar'", role);
        }
    }

    @Test
    void theEntityMatchesTheTableAndStartsCommonAndUnverified() {
        users.saveAndFlush(user("ana@uade.edu.ar", "1111111"));

        Map<String, Object> row = jdbc.queryForMap(
                "SELECT role, email_verified, declared_user_type FROM app_user WHERE email = 'ana@uade.edu.ar'");
        assertThat(row.get("role")).isEqualTo("USER");
        assertThat(row.get("email_verified")).isEqualTo(false);
        assertThat(row.get("declared_user_type")).isEqualTo("STUDENT");
    }

    @Test
    void existsByFindsTheAccountByEmailAndByLegajo() {
        users.saveAndFlush(user("ana@uade.edu.ar", "1111111"));

        assertThat(users.existsByEmail("ana@uade.edu.ar")).isTrue();
        assertThat(users.existsByEmail("otra@uade.edu.ar")).isFalse();
        assertThat(users.existsByLegajo("1111111")).isTrue();
        assertThat(users.existsByLegajo("2222222")).isFalse();
    }

    @Test
    void theDatabaseRejectsASecondAccountWithTheSameEmail() {
        users.saveAndFlush(user("ana@uade.edu.ar", "1111111"));

        assertThatThrownBy(() -> users.saveAndFlush(user("ana@uade.edu.ar", "2222222")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void theDatabaseRejectsASecondAccountWithTheSameLegajo() {
        users.saveAndFlush(user("ana@uade.edu.ar", "1111111"));

        assertThatThrownBy(() -> users.saveAndFlush(user("otra@uade.edu.ar", "1111111")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void theDatabaseRejectsAnEmailWithUpperCase() {
        assertThatThrownBy(() -> users.saveAndFlush(user("Ana@uade.edu.ar", "1111111")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void aRowInsertedByHandIsCommonAndUnverifiedByDefault() {
        insertByHand(null);

        Map<String, Object> row = jdbc.queryForMap("SELECT role, email_verified FROM app_user");
        assertThat(row.get("role")).isEqualTo("USER");
        assertThat(row.get("email_verified")).isEqualTo(false);
    }

    @Test
    void theDatabaseAcceptsOnlyTheKnownRoles() {
        assertThatThrownBy(() -> insertByHand("SUPERUSER")).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void anAdminCanBePromotedByHandWithPlainSql() {
        // Es como se crea el primer ADMIN (docs/reglas/usuarios-y-prioridad.md): un UPDATE en la consola de la base.
        insertByHand("ADMIN");

        assertThat(jdbc.queryForObject("SELECT role FROM app_user", String.class)).isEqualTo("ADMIN");
    }
}
