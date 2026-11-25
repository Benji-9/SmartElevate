package com.smartelevate.user.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartelevate.common.db.PostgresTestcontainersConfig;
import com.smartelevate.priority.model.PriorityCategory;
import com.smartelevate.priority.model.PriorityStatus;
import com.smartelevate.priority.model.UserPriority;
import com.smartelevate.priority.repository.UserPriorityRepository;
import com.smartelevate.user.model.DeclaredUserType;
import com.smartelevate.user.model.EmailToken;
import com.smartelevate.user.model.TokenPurpose;
import com.smartelevate.user.model.User;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * La limpieza de cuentas sin verificar (#344) contra Postgres real: el borrado en bloque con subconsultas y la
 * cascada de los tokens se probaron en H2, pero producción corre en Postgres. Sin Docker se saltea; en CI corre siempre.
 */
@SpringBootTest
@Import(PostgresTestcontainersConfig.class)
@Testcontainers(disabledWithoutDocker = true)
class UnverifiedCleanupPostgresTest {

    private static final Instant NOW = Instant.parse("2026-11-25T15:00:00Z");
    private static final Instant CUTOFF = NOW.minus(Duration.ofDays(7));

    @Autowired
    private UserRepository users;

    @Autowired
    private UserPriorityRepository priorities;

    @Autowired
    private EmailTokenRepository emailTokens;

    @AfterEach
    void clean() {
        emailTokens.deleteAll();
        priorities.deleteAll();
        users.deleteAll();
    }

    private User account(String email, String legajo, Instant createdAt, boolean verified) {
        User user = new User("Ana Pérez", email, legajo, "{noop}x", DeclaredUserType.STUDENT, createdAt);
        if (verified) {
            user.verifyEmail();
        }
        return users.saveAndFlush(user);
    }

    @Test
    void deletesOnlyOldUnverifiedAccountsAndTakesTheirTokensWithThem() {
        User abandoned = account("abandonada@uade.edu.ar", "1000001", CUTOFF.minusSeconds(1), false);
        account("reciente@uade.edu.ar", "1000002", CUTOFF.plusSeconds(1), false);
        account("verificada@uade.edu.ar", "1000003", CUTOFF.minus(Duration.ofDays(30)), true);
        emailTokens.save(new EmailToken(abandoned.getId(), TokenPurpose.VERIFY_EMAIL, "hash-de-prueba", NOW,
                NOW.plusSeconds(60)));

        int deleted = users.deleteStaleUnverified(CUTOFF);

        assertThat(deleted).isEqualTo(1);
        assertThat(users.findAll()).extracting(User::getEmail)
                .containsExactlyInAnyOrder("reciente@uade.edu.ar", "verificada@uade.edu.ar");
        assertThat(emailTokens.count()).isZero();
    }

    @Test
    void anOldUnverifiedAccountWithPriorityHistoryIsKept() {
        User withHistory = account("con-historia@uade.edu.ar", "1000004", CUTOFF.minusSeconds(1), false);
        UserPriority row = new UserPriority(withHistory.getId(), PriorityCategory.TEACHER);
        row.decide(PriorityStatus.PENDING, null, NOW, UUID.randomUUID());
        priorities.saveAndFlush(row);

        assertThat(users.deleteStaleUnverified(CUTOFF)).isZero();
        assertThat(users.existsByEmail("con-historia@uade.edu.ar")).isTrue();
    }
}
