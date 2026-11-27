package com.smartelevate.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smartelevate.priority.model.PriorityCategory;
import com.smartelevate.priority.model.PriorityStatus;
import com.smartelevate.priority.model.UserPriority;
import com.smartelevate.priority.repository.UserPriorityRepository;
import com.smartelevate.user.model.DeclaredUserType;
import com.smartelevate.user.model.Role;
import com.smartelevate.user.model.TokenPurpose;
import com.smartelevate.user.model.User;
import com.smartelevate.user.repository.EmailTokenRepository;
import com.smartelevate.user.repository.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Limpieza de cuentas sin verificar (#344), contra la base real. Sin la transacción del test: cada borrado va en la
 * suya, como en el job, y uno que falla por FK no arrastra a los demás.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class UnverifiedAccountCleanupTest {

    private static final Instant NOW = Instant.parse("2026-11-26T13:00:00Z");
    private static final Duration TTL = Duration.ofDays(2);

    @Autowired
    private UserRepository users;

    @Autowired
    private EmailTokenRepository emailTokens;

    @Autowired
    private UserPriorityRepository priorities;

    private UnverifiedAccountCleanup cleanup() {
        return new UnverifiedAccountCleanup(users,
                new VerificationProperties(Duration.ofHours(24), Duration.ofMinutes(1), TTL, "https://app.test"),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @BeforeEach
    @AfterEach
    void clean() {
        priorities.deleteAll();
        emailTokens.deleteAll();
        users.deleteAll();
    }

    private User user(String name, Instant createdAt, Consumer<User> setup) {
        User user = new User(name, name + "@uade.edu.ar", String.valueOf(Math.abs(name.hashCode())), "{noop}x",
                DeclaredUserType.STUDENT, createdAt);
        setup.accept(user);
        return users.saveAndFlush(user);
    }

    @Test
    void deletesUnverifiedAccountsOlderThanTtl() {
        User old = user("viejo", NOW.minus(TTL).minusSeconds(1), u -> { });
        new EmailTokenService(emailTokens, Clock.fixed(NOW, ZoneOffset.UTC))
                .issue(old.getId(), TokenPurpose.VERIFY_EMAIL, Duration.ofHours(24));

        assertThat(cleanup().deleteExpired()).isEqualTo(1);

        assertThat(users.findById(old.getId())).isEmpty();
        assertThat(emailTokens.count()).isZero();
    }

    @Test
    void keepsRecentUnverified() {
        User atTheLimit = user("justo", NOW.minus(TTL), u -> { });
        User recent = user("nuevo", NOW.minusSeconds(60), u -> { });

        assertThat(cleanup().deleteExpired()).isZero();

        assertThat(users.findById(atTheLimit.getId())).isPresent();
        assertThat(users.findById(recent.getId())).isPresent();
    }

    @Test
    void keepsVerifiedAndAdmins() {
        User verified = user("verificado", NOW.minus(Duration.ofDays(30)), User::verifyEmail);
        User admin = user("admin", NOW.minus(Duration.ofDays(30)), u -> u.assignRole(Role.ADMIN));

        assertThat(cleanup().deleteExpired()).isZero();

        assertThat(users.findById(verified.getId())).isPresent();
        assertThat(users.findById(admin.getId())).isPresent();
    }

    @Test
    void anAccountBlockedByAForeignKeyIsSkippedAndTheRestAreDeleted() {
        User referenced = user("referenciado", NOW.minus(Duration.ofDays(3)), u -> { });
        UserPriority priority = new UserPriority(referenced.getId(), PriorityCategory.TEACHER);
        priority.decide(PriorityStatus.REJECTED, null, NOW, referenced.getId());
        priorities.saveAndFlush(priority);
        User old = user("viejo", NOW.minus(Duration.ofDays(3)), u -> { });

        assertThat(cleanup().deleteExpired()).isEqualTo(1);

        assertThat(users.findById(referenced.getId())).isPresent();
        assertThat(users.findById(old.getId())).isEmpty();
    }

    @Test
    void aTtlNotLongerThanTheVerificationLinkIsRefused() {
        // Si no, se borraría una cuenta con el link todavía vigente.
        assertThatThrownBy(() -> new VerificationProperties(Duration.ofHours(24), Duration.ofMinutes(1),
                Duration.ofHours(24), "https://app.test")).isInstanceOf(IllegalArgumentException.class);
    }
}
