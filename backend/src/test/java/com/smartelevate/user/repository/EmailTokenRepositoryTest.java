package com.smartelevate.user.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartelevate.user.model.DeclaredUserType;
import com.smartelevate.user.model.EmailToken;
import com.smartelevate.user.model.TokenPurpose;
import com.smartelevate.user.model.User;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

/** La query de {@code markUsed}: es la que garantiza "un solo uso" y "vence" (V8__email_tokens.sql). */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class EmailTokenRepositoryTest {

    private static final Instant NOW = Instant.parse("2026-10-13T15:00:00Z");

    @Autowired
    private EmailTokenRepository tokens;

    @Autowired
    private UserRepository users;

    private void save(String hash, Instant expiresAt) {
        User user = users.saveAndFlush(new User("Ana", "ana@uade.edu.ar", "1111111", "x", DeclaredUserType.STUDENT, NOW));
        tokens.saveAndFlush(new EmailToken(user.getId(), TokenPurpose.VERIFY_EMAIL, hash, NOW.minusSeconds(60), expiresAt));
    }

    @Test
    void aTokenCanBeMarkedUsedOnlyOnce() {
        save("aaa", NOW.plusSeconds(60));

        assertThat(tokens.markUsed("aaa", TokenPurpose.VERIFY_EMAIL, NOW)).isEqualTo(1);
        assertThat(tokens.markUsed("aaa", TokenPurpose.VERIFY_EMAIL, NOW)).isZero();
        assertThat(tokens.findByTokenHash("aaa").orElseThrow().getUsedAt()).isEqualTo(NOW);
    }

    @Test
    void anExpiredTokenCannotBeUsed() {
        save("bbb", NOW);

        assertThat(tokens.markUsed("bbb", TokenPurpose.VERIFY_EMAIL, NOW)).isZero();
    }

    @Test
    void aTokenOfAnotherPurposeCannotBeUsed() {
        save("ccc", NOW.plusSeconds(60));

        assertThat(tokens.markUsed("ccc", TokenPurpose.PASSWORD_RESET, NOW)).isZero();
    }
}
